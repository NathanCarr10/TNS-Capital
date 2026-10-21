import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';

import { AuthService } from '../auth/auth.service';

export interface SupportMessage {
  id: number;
  senderEmail: string;
  senderRole: 'CUSTOMER' | 'SUPPORT';
  body: string;
  createdAt: string;
}

export interface SupportConversation {
  id: string;
  customerEmail: string;
  accountId: number | null;
  subject: string;
  status: 'OPEN' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
  messages: SupportMessage[];
}

@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly endpoint = '/api/v1/support/conversations';

  getConversations(): Observable<SupportConversation[]> {
    return this.http.get<SupportConversation[]>(this.endpoint);
  }

  createConversation(subject: string, body: string): Observable<SupportConversation> {
    return this.http.post<SupportConversation>(this.endpoint, { subject, body });
  }

  addMessage(conversationId: string, body: string): Observable<SupportConversation> {
    return this.http.post<SupportConversation>(`${this.endpoint}/${conversationId}/messages`, { body });
  }

  closeConversation(conversationId: string): Observable<SupportConversation> {
    return this.http.post<SupportConversation>(`${this.endpoint}/${conversationId}/close`, {});
  }

  getUnreadNotificationCount(): Observable<{ unreadCount: number }> {
    return this.http.get<{ unreadCount: number }>(`${this.endpoint}/notifications/unread`);
  }

  markNotificationsRead(): Observable<void> {
    return this.http.post<void>(`${this.endpoint}/notifications/read`, {});
  }

  /**
   * Follows a conversation's new messages until the controller is aborted. The server checks
   * access only when a stream opens, so it ends each stream after 15 minutes (OWASP review
   * A01-13); this reconnects with the current access token, refreshing it once if it has expired.
   */
  connectToConversation(conversationId: string, onMessage: (message: SupportMessage) => void): AbortController {
    const controller = new AbortController();
    void this.followEvents(conversationId, controller.signal, onMessage);
    return controller;
  }

  private async followEvents(conversationId: string, signal: AbortSignal,
      onMessage: (message: SupportMessage) => void): Promise<void> {
    let refreshed = false;
    while (!signal.aborted) {
      const opened = Date.now();
      const outcome = await this.readEvents(conversationId, signal, onMessage);
      if (outcome === 'unauthorised') {
        if (refreshed || !this.auth.canRefresh() || !(await this.refreshToken())) return;
        refreshed = true;
      } else if (outcome === 'refused') {
        return;
      } else {
        refreshed = false;
        // A stream that ends or fails straight away is retried after a pause, not in a tight loop
        if (outcome === 'failed' || Date.now() - opened < MIN_STREAM_MS) await pause(RETRY_DELAY_MS, signal);
      }
    }
  }

  private async refreshToken(): Promise<boolean> {
    try {
      await firstValueFrom(this.auth.refresh());
      return true;
    } catch {
      return false;
    }
  }

  private async readEvents(conversationId: string, signal: AbortSignal,
      onMessage: (message: SupportMessage) => void): Promise<StreamOutcome> {
    try {
      const token = this.auth.getAccessToken();
      const response = await fetch(`${this.endpoint}/${conversationId}/events`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal,
      });
      if (response.status === 401) return 'unauthorised';
      if (!response.ok || !response.body) return 'refused';
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = '';
      while (!signal.aborted) {
        const chunk = await reader.read();
        if (chunk.done) break;
        buffer += chunk.value;
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';
        for (const event of events) {
          const data = event.split('\n').find((line) => line.startsWith('data:'))?.slice(5).trim();
          if (data && data !== 'connected') onMessage(JSON.parse(data) as SupportMessage);
        }
      }
      return 'ended';
    } catch {
      // Aborted streams are expected when the agent switches conversations
      return 'failed';
    }
  }
}

/** How a stream finished: closed by the server, refused for the token, refused otherwise, or broken. */
type StreamOutcome = 'ended' | 'unauthorised' | 'refused' | 'failed';

/** A stream that closes sooner than this is reconnected after RETRY_DELAY_MS. */
const MIN_STREAM_MS = 5_000;
const RETRY_DELAY_MS = 3_000;

function pause(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    }, { once: true });
  });
}
