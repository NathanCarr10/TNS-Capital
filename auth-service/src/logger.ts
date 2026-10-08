// No parameter for a password or token, so neither can reach the logs by accident.
export function logAuthEvent(event: string, username: string): void {
  console.log(`[auth] ${event} username=${username}`);
}
