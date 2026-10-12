output "url" {
  description = "Public URL of the app"
  value       = google_cloud_run_v2_service.app.uri
}

output "image_repository" {
  description = "Push the image here before the first apply of the Cloud Run service"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.app.repository_id}"
}

output "db_connection_name" {
  value = google_sql_database_instance.db.connection_name
}
