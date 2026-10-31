package ng.itlearning.pay.assistant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Loads the help centre into pgvector at start-up (topic 15.2): one chunk per "## " section, so each chunk
 * answers one question and carries its title. A file is re-embedded only when its content changes.
 */
@Component
class HelpDocs implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HelpDocs.class);

    private final VectorStore store;
    private final JdbcClient jdbc;
    // Sections are short; the splitter only matters if someone writes a long one
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(400).build();

    HelpDocs(VectorStore store, JdbcClient jdbc) {
        this.store = store;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath:help/*.md")) {
            String name = file.getFilename();
            String text = file.getContentAsString(StandardCharsets.UTF_8);
            String hash = sha256(text);
            boolean unchanged = jdbc.sql("SELECT count(*) FROM help_doc WHERE source = ? AND sha256 = ?")
                    .params(name, hash).query(Integer.class).single() > 0;
            if (unchanged) {
                continue;
            }
            store.delete(new FilterExpressionBuilder().eq("source", name).build());   // the old version's chunks
            List<Document> chunks = splitter.apply(sections(name, text));
            store.add(chunks);                                                       // embeds every chunk
            jdbc.sql("""
                    INSERT INTO help_doc (source, sha256, chunks) VALUES (?, ?, ?)
                    ON CONFLICT (source) DO UPDATE SET sha256 = excluded.sha256, chunks = excluded.chunks, loaded_at = now()""")
                    .params(name, hash, chunks.size()).update();
            log.info("Embedded {} chunks from {}", chunks.size(), name);
        }
    }

    /** "# Page" then "## Section" blocks: each section becomes a document that starts with both titles. */
    static List<Document> sections(String source, String markdown) {
        String page = "";
        List<Document> docs = new ArrayList<>();
        for (String block : markdown.split("(?m)^(?=## )")) {
            if (block.startsWith("# ")) {
                page = block.lines().findFirst().orElse("").substring(2).trim();
                continue;
            }
            String section = block.lines().findFirst().orElse("").substring(3).trim();
            docs.add(new Document(page + " > " + section + "\n" + block.substring(block.indexOf('\n') + 1).trim(),
                    Map.of("source", source, "title", page + " > " + section)));
        }
        return docs;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
