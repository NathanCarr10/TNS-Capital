# The LEAP training account (eu-west-1). Teammates: copy this with your own prefix.
# Subnet and VPC IDs are the class's shared default VPC (see ~/leap-env.sh).
prefix = "nathan-tns"
region = "eu-west-1"

vpc_id             = "vpc-07c5d2a3f09dffc30"
public_subnet_ids  = ["subnet-048ea092ac9ad358b", "subnet-05c0d4425955ba550"]
private_subnet_ids = ["subnet-05ab71181c3288f8f", "subnet-084c7b53a1d0554d0"]

cpu_architecture = "X86_64"
