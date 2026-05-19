{{- define "modern-ecommerce.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "modern-ecommerce.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "modern-ecommerce.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "modern-ecommerce.labels" -}}
app.kubernetes.io/name: {{ include "modern-ecommerce.name" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: modern-ecommerce
{{- end -}}

{{- define "modern-ecommerce.componentLabels" -}}
{{ include "modern-ecommerce.labels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "modern-ecommerce.componentName" -}}
{{ include "modern-ecommerce.fullname" .root }}-{{ .component }}
{{- end -}}

{{- define "modern-ecommerce.image" -}}
{{- $registry := trimSuffix "/" .root.Values.global.imageRegistry -}}
{{- $repository := .workload.image.repository -}}
{{- $tag := default .root.Values.global.imageTag .workload.image.tag -}}
{{- printf "%s/%s:%s" $registry $repository $tag -}}
{{- end -}}
