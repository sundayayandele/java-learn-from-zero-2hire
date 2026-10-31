<?php
// Point the Ollama AI provider on itlearning.tech at the Ollama container directly (run once, inside the Moodle container).
//
// The provider had been pointed at https://chat.itlearning.tech/ (Open WebUI, not the Ollama API, so every call failed),
// logged in with an admin password, and asked for llama3.3 (70B, not installed; too big for this server).
// Moodle and Ollama share the itlearning-tech_default Docker network, so http://ollama:11434 needs no login and
// nothing has to be published to the internet.
//
//   docker cp fix_provider.php moodle:/tmp/ && docker exec -u www-data moodle php /tmp/fix_provider.php [--model=llama3.2:1b]
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');

[$opts] = cli_get_params(['model' => 'llama3.2:1b', 'endpoint' => 'http://ollama:11434'], []);

$manager = \core\di::get(\core_ai\manager::class);
$providers = $manager->get_provider_instances(['provider' => 'aiprovider_ollama\\provider']);
if (!$providers) {
    cli_error('No Ollama provider is configured.');
}
foreach ($providers as $provider) {
    $config = $provider->config;
    $config['endpoint'] = $opts['endpoint'];
    $config['enablebasicauth'] = 0;
    unset($config['username'], $config['password']);       // never keep a person's password in provider settings
    $actions = $provider->actionconfig;
    foreach ($actions as $action => $settings) {
        $actions[$action]['settings']['model'] = $opts['model'];
    }
    $manager->update_provider_instance($provider, $config, $actions);
    echo "Provider {$provider->id} ({$provider->name}): endpoint {$opts['endpoint']}, no login, model {$opts['model']} for "
        . count($actions) . " actions\n";
}
purge_caches();
