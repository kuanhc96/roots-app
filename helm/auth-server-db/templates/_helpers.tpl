{{/*
Chart name, overridable. Derived from .Chart.Name rather than hardcoded so renaming
the chart does not require editing every template.
*/}}
{{- define "auth-server-db.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Fully qualified name. The `contains` guard is what keeps
`helm install auth-server-db ./helm/auth-server-db` from producing
"auth-server-db-auth-server-db": when the release name already contains the chart
name, the release name is used as-is. This is what makes the Service resolve at
`auth-server-db`, matching the hostname docker-compose uses.
*/}}
{{- define "auth-server-db.fullname" -}}
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

{{- define "auth-server-db.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Selector labels — deliberately kept separate from the full label set below.

A StatefulSet's spec.selector is IMMUTABLE. If a mutable label
(app.kubernetes.io/version, helm.sh/chart) ever leaked into the selector, the next
image-tag bump would fail the upgrade with "field is immutable" and require deleting
the StatefulSet by hand. Only stable identity labels belong here.
*/}}
{{- define "auth-server-db.selectorLabels" -}}
app.kubernetes.io/name: {{ include "auth-server-db.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "auth-server-db.labels" -}}
helm.sh/chart: {{ include "auth-server-db.chart" . }}
{{ include "auth-server-db.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
