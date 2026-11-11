{{/*
Chart name, overridable. Derived from .Chart.Name rather than hardcoded so the
chart can be renamed (or promoted to a shared "redis" chart) without editing
every template.
*/}}
{{- define "auth-server-redis.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Fully qualified name. The `contains` guard is what keeps
`helm install auth-server-redis ./helm/auth-server-redis` from producing
"auth-server-redis-auth-server-redis": when the release name already contains
the chart name, the release name is used as-is.

This matters beyond cosmetics — it makes the Service resolve at exactly
`auth-server-redis`, so REDIS_HOST is character-identical to the value
docker-compose passes auth-server.
*/}}
{{- define "auth-server-redis.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "auth-server-redis.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Selector labels — deliberately kept separate from the full label set below.

A Deployment's spec.selector is IMMUTABLE. If a mutable label (app.kubernetes.io/version,
helm.sh/chart) ever leaked into the selector, the next image-tag bump would fail the
upgrade with "field is immutable" and require deleting the Deployment by hand.
Only stable identity labels belong here.
*/}}
{{- define "auth-server-redis.selectorLabels" -}}
app.kubernetes.io/name: {{ include "auth-server-redis.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "auth-server-redis.labels" -}}
helm.sh/chart: {{ include "auth-server-redis.chart" . }}
{{ include "auth-server-redis.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
