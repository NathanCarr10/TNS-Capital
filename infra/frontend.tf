# The Angular build in a private S3 bucket, served by CloudFront. The same distribution
# forwards /api, /auth and /analytics to the ALB, so the app keeps calling its own
# origin (as it does through nginx locally) and needs no CORS or URL changes.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "frontend" {
  bucket        = "${var.prefix}-frontend-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.prefix}-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Only this distribution may read the bucket
resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.this.arn } }
    }]
  })
}

# Client-side routes (/dashboard, /symbols/AAPL) load index.html, like nginx's
# try_files. Done per request rather than with a custom error page, because error
# pages apply to the whole distribution and would turn API 403/404s into index.html.
resource "aws_cloudfront_function" "spa_routes" {
  name    = "${var.prefix}-spa-routes"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var last = request.uri.split('/').pop();
      if (last.indexOf('.') === -1) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOT
}

# frontend/security-headers.conf, plus HSTS now that the site is served over HTTPS
resource "aws_cloudfront_response_headers_policy" "app" {
  name = "${var.prefix}-app-headers"

  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'self'; script-src 'self' 'unsafe-hashes' 'sha256-MhtPZXr7+LpJUY5qtMutB+qWfQtMaPccfe7QXtCcEYc='; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
      override                = true
    }
    content_type_options {
      override = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = false
      override                   = true
    }
  }
}

# Streamlit needs inline scripts and its own WebSocket, which the app's CSP blocks;
# nginx gives /analytics/ only nosniff for the same reason
resource "aws_cloudfront_response_headers_policy" "analytics" {
  name = "${var.prefix}-analytics-headers"

  security_headers_config {
    content_type_options {
      override = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = false
      override                   = true
    }
  }
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

# Every header, cookie and query string, Host included: Streamlit rejects a WebSocket
# whose Origin doesn't match the Host, and the ALB has no host rules to trip over it
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

locals {
  s3_origin  = "s3-frontend"
  alb_origin = "alb-api"

  api_paths = {
    "/api/*"       = aws_cloudfront_response_headers_policy.app.id
    "/auth/*"      = aws_cloudfront_response_headers_policy.app.id
    "/analytics"   = aws_cloudfront_response_headers_policy.analytics.id
    "/analytics/*" = aws_cloudfront_response_headers_policy.analytics.id
  }
}

resource "aws_cloudfront_distribution" "this" {
  enabled             = true
  comment             = "${var.prefix} TNS Capital"
  default_root_object = "index.html"
  price_class         = "PriceClass_100" # Europe and North America edges only
  http_version        = "http2"

  origin {
    origin_id                = local.s3_origin
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # CloudFront -> ALB stays HTTP: the viewer side is HTTPS on CloudFront's certificate,
  # and the ALB accepts CloudFront's addresses only
  origin {
    origin_id   = local.alb_origin
    domain_name = aws_lb.this.dns_name
    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_protocol_policy   = "http-only"
      origin_ssl_protocols     = ["TLSv1.2"]
      origin_read_timeout      = 60
      origin_keepalive_timeout = 60
    }
  }

  default_cache_behavior {
    target_origin_id           = local.s3_origin
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.app.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_routes.arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = local.api_paths
    content {
      path_pattern               = ordered_cache_behavior.key
      target_origin_id           = local.alb_origin
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods             = ["GET", "HEAD"]
      compress                   = false
      cache_policy_id            = data.aws_cloudfront_cache_policy.disabled.id
      origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer.id
      response_headers_policy_id = ordered_cache_behavior.value
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
