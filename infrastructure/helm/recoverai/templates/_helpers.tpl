{{- define "recoverai.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "recoverai.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "recoverai.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "recoverai.labels" -}}
app.kubernetes.io/name: {{ include "recoverai.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: recoverai
{{- end }}
