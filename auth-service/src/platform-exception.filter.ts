import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus } from "@nestjs/common";
import { Request, Response } from "express";

// The platform error envelope from contracts/auth-api.yaml, shared with the
// trading API and the auth stub.
export interface ErrorEnvelope {
  errorCode: string;
  message: string;
  timestamp: string;
}

interface PlatformError {
  status: number;
  errorCode: string;
  message: string;
}

@Catch()
export class PlatformExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const { status, errorCode, message } = toPlatformError(exception, http.getRequest<Request>());
    const body: ErrorEnvelope = { errorCode, message, timestamp: new Date().toISOString() };
    http.getResponse<Response>().status(status).json(body);
  }
}

function toPlatformError(exception: unknown, request: Request): PlatformError {
  if (!(exception instanceof HttpException)) {
    console.error("Unhandled exception:", exception);
    return serverError();
  }

  const status = exception.getStatus();
  const response = exception.getResponse();
  if (isPlatformBody(response)) {
    return { status, errorCode: response.errorCode, message: response.message };
  }

  switch (status) {
    case HttpStatus.BAD_REQUEST:
      return { status, errorCode: "VAL-422", message: validationMessage(response) };
    case HttpStatus.UNAUTHORIZED:
      return { status, errorCode: "AUTH-401", message: "Unauthorised or invalid token" };
    case HttpStatus.NOT_FOUND:
      return { status, errorCode: "NOT-404", message: `Resource not found: ${request.method} ${request.path}` };
    default:
      return status >= 500 ? serverError() : { status, errorCode: `REQ-${status}`, message: exception.message };
  }
}

function serverError(): PlatformError {
  return {
    status: HttpStatus.INTERNAL_SERVER_ERROR,
    errorCode: "SYS-500",
    message: "An unexpected error occurred. Please contact support with error timestamp if problem persists.",
  };
}

function isPlatformBody(response: unknown): response is { errorCode: string; message: string } {
  return (
    typeof response === "object" &&
    response !== null &&
    typeof (response as Record<string, unknown>).errorCode === "string" &&
    typeof (response as Record<string, unknown>).message === "string"
  );
}

// ValidationPipe reports field problems as a string array, which is safe to
// show. Any other 400 (such as unparseable JSON) gets a fixed message so parser
// internals never reach the client.
function validationMessage(response: unknown): string {
  const message = typeof response === "object" && response !== null ? (response as { message?: unknown }).message : undefined;
  if (Array.isArray(message) && message.every((m) => typeof m === "string")) {
    return message.join("; ");
  }
  return "Request body is missing or malformed";
}
