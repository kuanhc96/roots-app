{{- define "auth-server-db.name" -}}
auth-server-db
{{- end -}}

{{- define "auth-server-db.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "auth-server-db.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "auth-server-db.labels" -}}
app.kubernetes.io/name: {{ include "auth-server-db.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
