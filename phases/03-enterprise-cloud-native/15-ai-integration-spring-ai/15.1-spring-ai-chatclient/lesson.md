## What you will be able to do

- Explain what a call to a large language model (LLM) is: tokens, a context window, no memory between calls, and answers that vary.
- Call a model from Spring Boot with Spring AI's `ChatClient`, against a local open-source model (Ollama) or a hosted one, by changing configuration.
- Keep prompts in files, separate system instructions from user input, and choose sensible model options.
- Get typed results back with structured output, and handle the times the model gets the format wrong.
- Estimate what a feature will cost and how slow it will be, from measured token counts.

## Why this matters in a bank or fintech

Banks and fintechs now use language models in support (answering "why did my transfer fail?"), operations (sorting complaints, summarising disputes) and engineering. The model is the easy part. The work a Java backend engineer is hired for is everything around it: calling it reliably, keeping customer data safe, grounding answers in the company's own documents, letting it read from your services without letting it move money, and knowing what it costs. This module builds that around one feature in ITL Pay: a support assistant.

## 1. What a model call is

A chat model takes a list of **messages** and returns the next message. Four facts shape everything you build:

- **Tokens.** Models read and write text in tokens, pieces of words (in English, roughly four characters each). You pay per token, in and out, and every model has a **context window**: the most tokens one call can hold.
- **No memory.** Each call is independent. A "conversation" is your code sending the earlier messages again.
- **Answers vary.** The model picks likely next tokens, with some randomness (the **temperature**). The same question can get different wording, and occasionally a different answer.
- **It can be confidently wrong.** A model produces plausible text, not checked facts. Topics 15.2 to 15.4 are about narrowing that gap.

Messages have **roles**. The **system** message is your instructions (who the assistant is, what it may do); the **user** message is the customer's input. Keep them apart: user text should never be pasted into your instructions.

## 2. Spring AI

Spring AI gives Java one API over many model providers, the way Spring Data does over databases. The pieces you use most:

| Piece | What it is |
| --- | --- |
| `ChatModel` | One provider's chat API (Ollama, Anthropic, OpenAI, Bedrock and others), configured by a starter |
| `ChatClient` | The fluent API you call: prompt, options, advisors, tools, and the response as text or an object |
| Advisors | Code that runs around each call, like servlet filters: retrieval (15.2), memory, logging, guardrails |
| `EmbeddingModel`, `VectorStore` | Turning text into vectors and searching them (15.2) |
| `@Tool` | Java methods the model may ask to call (15.3) |

The starter uses **Spring AI 2.0.2** on Spring Boot 4.1, imported with its BOM, and one starter per provider:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>2.0.2</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
...
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

Boot auto-configures a `ChatClient.Builder`; you build one client per purpose:

```java
ChatClient chat = builder.defaultSystem("You are the ITL Pay support assistant. ...").build();

String answer = chat.prompt()
        .user("What is the daily limit for a Tier 1 account?")
        .call()
        .content();
```

## 3. A local model with Ollama

[Ollama](https://ollama.com) runs open-weight models on your own machine: free, private and offline, which suits learning and is a real option where customer data mustn't leave your network. Install it, then pull a chat model and an embedding model:

```bash
ollama pull qwen3:4b-instruct   # 2.5 GB: a small chat model that supports tool calling
ollama pull nomic-embed-text    # 274 MB: embeddings for topic 15.2
ollama pull qwen3:1.7b          # 1.4 GB, optional: for 8 GB machines, and for comparison in 15.4
```

```properties
spring.ai.ollama.base-url=${OLLAMA_URL:http://localhost:11434}
spring.ai.ollama.chat.model=${CHAT_MODEL:qwen3:4b-instruct}
spring.ai.ollama.chat.temperature=0.2      # low: support answers should be consistent, not creative
spring.ai.ollama.chat.num-predict=400      # at most 400 output tokens per answer: a cap on cost and time
spring.ai.ollama.chat.think=false          # don't "think" before answering: slower, and not needed here
```

**Choosing a local model.** Bigger models answer better and need more memory and time. A model needs roughly its download size in RAM, plus room for the context. Check that it supports **tools** (`ollama show qwen3:1.7b` lists its capabilities) if you'll need topic 15.3: the `gemma3:1b` model, for example, doesn't. And check whether it is a **thinking** model: Ollama's `qwen3:4b` is the thinking-only variant, which ignores `think=false` and prints its reasoning before every answer (topic 15.4 shows what that did); `qwen3:4b-instruct` is the same size without it. Run Ollama natively rather than in Docker on a Mac, so it can use the GPU; the starter's containers reach it at `host.docker.internal`.

**What we measured** on the starter's laptop (an Intel Core i7 with no usable GPU, so the slowest realistic case): with `qwen3:1.7b`, the first question after start-up took **24.8 s**, because Ollama loaded the model into memory; after that, answers took 2–12 s (p50 **5.7 s** over 18 questions). `qwen3:4b-instruct` took p50 **11.8 s** and p95 20.9 s, and answered far better (15.4). A recent Mac with Apple silicon, or any machine with a GPU, is several times faster.

## 4. Prompts are code

Keep prompts in files under `src/main/resources/prompts/`, review them like code and test them (topic 15.4). The starter's assistant loads its system prompt from `support-system.st`:

```java
@Value("classpath:prompts/support-system.st") Resource supportSystem;
...
chat.clone().defaultSystem(supportSystem).build();
```

The `.st` extension is for StringTemplate: placeholders like `{query}` are filled in at call time. A good system prompt for a bank says, briefly and concretely:

- who the assistant is and what it covers, and what to do outside that ("say you don't know and suggest contacting support");
- where facts must come from ("use only the help-centre excerpts and tool results; never guess fees, limits, dates or balances");
- what it can't do, so it never claims to ("you can only read information; never say you've sent or reversed money");
- what it must never ask for (PINs, one-time codes);
- the format (four short sentences, plain English).

Instructions reduce bad answers; they don't prevent them. A small model will still break rules sometimes, which is why money facts come from tools (15.3) and why you measure (15.4).

## 5. Structured output

Often you don't want prose; you want data your code can act on. The assistant's triage endpoint sorts a support message into a category and urgency for the support queue:

```java
enum Category { TRANSFER_PROBLEM, ACCOUNT_SECURITY, FEES_AND_LIMITS, FX, DISPUTE, OTHER }
enum Urgency { LOW, NORMAL, HIGH }
record Triage(Category category, Urgency urgency, String summary) { }

Triage t = triage.prompt().user(message).call().entity(Triage.class);
```

`entity(Triage.class)` adds format instructions to the prompt, including a **JSON Schema** of the record (the starter's test checks they are there), then parses the model's JSON into the record. Enums constrain the answer to values your code knows. Given "Someone called from ITL Pay and I gave them my PIN", `qwen3:4b-instruct` returned `ACCOUNT_SECURITY`, `HIGH` and the summary "Shared PIN with unauthorized caller poses significant security risk", in 15 s.

Two cautions. The model can still return invalid JSON or an unexpected value, so be ready for a parsing exception (retry once, then route to a person). And a typed result is not a **correct** result: "HIGH" is the model's opinion, so use it to sort a queue, not to block an account.

## 6. Switching to a hosted model

Hosted models (Anthropic's Claude, OpenAI, Google Gemini, or the same through AWS Bedrock or Azure) are much stronger than anything a laptop runs, and you pay per token. With Spring AI the switch is a dependency and configuration, not code:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

```properties
spring.ai.model.chat=anthropic                          # which starter provides the ChatModel
spring.ai.model.embedding=ollama                        # Anthropic has no embedding models: keep Ollama, or use another provider's
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}        # from a secret, never from Git
spring.ai.anthropic.chat.model=claude-haiku-5-5
spring.ai.anthropic.chat.max-tokens=400
```

Remove `temperature` when you switch to Claude: current Claude models don't accept sampling settings. Before you send a single customer message to a hosted model, read topic 15.4: the data leaves your network, so it must be minimised, and your contract and Nigeria's data-protection law (the NDPA 2023) decide what you may send.

## 7. Cost and latency

Measure before you estimate. The starter returns each answer's token counts, and Spring AI records them as metrics (`gen_ai.client.token.usage`) and traces every call (Module 13). Over the 18 evaluation questions with `qwen3:4b-instruct`, one assistant answer used on average **1,119 input tokens and 42 output tokens**. Input dominates, because each call carries the system prompt, the retrieved help sections (15.2) and the tool definitions (15.3).

At 10,000 questions a day, with prices at the time of writing (per million tokens):

| Model | Input / output price | Cost per question | Per day |
| --- | --- | --- | --- |
| Local (Ollama) | Free; you pay for the machine | – | Hardware and power |
| Claude Haiku 5.5 | US$0.10 / US$0.50 | about US$0.00013 | about US$1.30 |
| Claude Opus 5.5 | US$4 / US$20 | about US$0.0053 | about US$53, more with thinking tokens |

Hosted token counts differ from Ollama's (each model has its own tokenizer), and models that think before answering bill those tokens as output, so measure on your real traffic before you commit. The cheapest model that passes your evaluation (15.4) is the right one.

For latency: set a **timeout** on model calls, cap output with `max-tokens` (`num-predict` on Ollama), keep prompts short, and for chat screens, **stream** the answer so the customer sees words straight away:

```java
Flux<String> words = chat.prompt().user(question).stream().content();   // send as server-sent events (topic 14.1)
```

## Worked example: the support assistant's first endpoint

ITL Pay's support team answers the same questions all day: fees, limits, what PENDING means. The first version of the assistant is a `ChatClient` with a system prompt from a file, calling a small Qwen3 model through Ollama, behind `POST /api/assistant/ask`:

1. The gateway routes `/api/assistant/**` to the `assistant` service.
2. The controller checks the customer and the question, then calls `Assistant.ask`.
3. `ChatClient` sends the system prompt and the question; the answer comes back with its token counts.
4. Separately, `POST /api/assistant/triage` returns a typed `Triage` for the support queue.

Asked "What is the daily limit for a Tier 1 account?", `qwen3:1.7b` answered "₦50,000" in 4.4 s, using 754 tokens in and 21 out. Without topic 15.2's retrieval, though, it has no way to know ITL Pay's limits: it would answer from whatever it learned in training, which is not your fee table.

## Common mistakes

- Pasting the user's text into the system prompt, so the customer can rewrite your rules.
- Expecting the model to remember the last message. Each call starts fresh unless you send the history.
- Trusting a typed result because it parsed: valid JSON can still hold a wrong answer.
- Choosing a local model that doesn't support tools, then wondering why tool calling (15.3) never happens.
- No timeout and no output cap: one slow or rambling answer holds a thread and costs money.
- Estimating cost from the question's length, forgetting the system prompt, retrieved documents and tool definitions sent with every call.
- An API key in `application.properties` in Git.

## Practice

1. Install Ollama, pull `qwen3:4b-instruct` and `nomic-embed-text`, and run the assistant (see the starter's README). Ask five questions and record the time and tokens of each, including the first.
2. Change `spring.ai.ollama.chat.think` to `true`, ask the same questions, and compare time and output tokens.
3. Add a field `boolean needsHuman` to `Triage`, test it with three messages, and handle a parsing failure by returning `OTHER` with `needsHuman = true`.
4. Stream an answer with `.stream().content()` from a new endpoint, and watch it arrive with `curl -N`.
5. If you have access to a hosted model, switch to it with configuration only and compare quality, time and the token counts it reports.

## Key terms

Large language model (LLM), token, context window, temperature, system message, user message, prompt template, Spring AI, `ChatModel`, `ChatClient`, advisor, Ollama, open-weight model, structured output, JSON Schema, streaming, cost per token.

## Further reading

- [Spring AI reference: ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI reference: Structured output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI reference: Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [Ollama documentation](https://docs.ollama.com)
- [Anthropic: Prompt engineering overview](https://docs.claude.com/en/docs/build-with-claude/prompt-engineering/overview)
