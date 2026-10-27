resource "aws_db_subnet_group" "this" {
  # Fixed name: the database can't be moved to another subnet group in the same VPC, so
  # renaming the prefix (nathan-tns -> tns-capital) must not replace it
  name        = "nathan-tns-db-subnets"
  description = "${var.prefix} private subnets for the database"
  subnet_ids  = var.private_subnet_ids
}

resource "aws_db_instance" "this" {
  identifier = "${var.prefix}-db"

  engine         = "postgres"
  engine_version = "16" # the major version db/Dockerfile uses
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"

  db_name  = "tns_capital"
  username = "tnsadmin"
  # RDS generates the master password and keeps it in Secrets Manager; it never
  # appears in this code, the state, or a terminal
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  # A training database: no backups, and destroy deletes it straight away
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true

  # The data is kept for the whole project: no plan may delete this instance (a rename or
  # tofu destroy fails here instead). Remove this only to tear down at the very end.
  lifecycle {
    prevent_destroy = true
  }
}
