resource "aws_db_subnet_group" "this" {
  name        = "${var.prefix}-db-subnets"
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
}
