{{/* Labels every object gets. */}}
{{- define "itl-pay.labels" -}}
app.kubernetes.io/part-of: itl-pay
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/* The image for one service: registry + itl-pay/<name>:<tag>. */}}
{{- define "itl-pay.image" -}}
{{ .root.Values.image.registry }}itl-pay/{{ .name }}:{{ .root.Values.image.tag }}
{{- end }}

{{/* Database and Kafka settings for a service that needs them. */}}
{{- define "itl-pay.connections" -}}
{{- if .svc.database }}
- name: DB_URL
  value: jdbc:postgresql://{{ .root.Values.infra.postgresHost }}:5432/{{ .svc.database }}
- name: DB_USER
  value: {{ .svc.database }}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .root.Values.databases.secretName }}
      key: {{ .svc.database }}
{{- end }}
{{- if .svc.kafka }}
- name: KAFKA_BOOTSTRAP
  value: {{ .root.Values.infra.kafkaBootstrap }}
{{- end }}
{{- if .svc.redis }}
- name: REDIS_HOST
  value: {{ .root.Values.infra.redisHost }}
{{- end }}
{{- with .root.Values.observability.otlpEndpoint }}
- name: OTLP_ENDPOINT
  value: {{ . }}
{{- else }}
- name: MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED      # no collector configured: trace ids in logs, nothing exported
  value: "false"
{{- end }}
{{- end }}

{{/* The pod-level and container-level security settings every workload uses (topic 12.1). */}}
{{- define "itl-pay.podSecurity" -}}
runAsNonRoot: true
runAsUser: 10001
seccompProfile:
  type: RuntimeDefault
{{- end }}
{{- define "itl-pay.containerSecurity" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}
