variable "project_id" {
  description = "GCP project id"
  type        = string
}

variable "region" {
  description = "Region for all resources. europe-north1 is Finland, the closest to Norway."
  type        = string
  default     = "europe-north1"
}

variable "image" {
  description = "Container image, e.g. europe-north1-docker.pkg.dev/<project>/fightcamp/app:1.0.0"
  type        = string
}

variable "db_tier" {
  description = "Cloud SQL machine tier"
  type        = string
  default     = "db-f1-micro"
}

variable "demo_data" {
  description = "Load the fake demo athlete and coach on start"
  type        = bool
  default     = true
}
