variable "region" {
  description = "AWS region. eu-west-1 (Ireland) or af-south-1 (Cape Town) are the usual choices for Nigerian teams."
  type        = string
  default     = "eu-west-1"
}

variable "name" {
  description = "Prefix for every resource"
  type        = string
  default     = "itl-pay"
}

variable "budget_email" {
  description = "Who gets the budget alerts"
  type        = string
}

variable "monthly_budget_usd" {
  description = "Alert at 80% and 100% of this. The EKS control plane alone is about US$73 a month."
  type        = number
  default     = 150
}
