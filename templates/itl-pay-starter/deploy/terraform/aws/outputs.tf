output "kubeconfig_command" {
  description = "Point kubectl at the new cluster"
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "postgres_host" {
  description = "infra.postgresHost in values-aws.yaml"
  value       = aws_db_instance.postgres.address
}

output "postgres_master_secret_arn" {
  description = "The Secrets Manager secret RDS created for the admin password"
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "image_registry" {
  description = "image.registry in values-aws.yaml"
  value       = "${split("/", aws_ecr_repository.service["gateway"].repository_url)[0]}/"
}

output "statements_bucket" {
  value = aws_s3_bucket.statements.bucket
}
