import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AuthService } from '../auth/auth.service';

export interface SupportMessage {
  id: number;
  senderUsername: string;
  senderRole: 'CUSTOMER' | 'SUPPORT';
  body: string;
  createdAt: string;
}

export interface SupportConversation {
  id: string;
  customerUsername: string;
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

  connectToConversation(conversationId: string, onMessage: (message: SupportMessage) => void): AbortController {
    const controller = new AbortController();
    const token = this.auth.getAccessToken();
    void this.readEvents(conversationId, token, controller.signal, onMessage);
    return controller;
  }

  private async readEvents(conversationId: string, token: string | null, signal: AbortSignal,
      onMessage: (message: SupportMessage) => void): Promise<void> {
    try {
      const response = await fetch(`${this.endpoint}/${conversationId}/events`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal,
      });
      if (!response.ok || !response.body) return;
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
    } catch {
      // Aborted streams are expected when the agent switches conversations.
    }
  }
}