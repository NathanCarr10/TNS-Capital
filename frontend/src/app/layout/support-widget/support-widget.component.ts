import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { SupportConversation, SupportMessage, SupportService } from '../../core/support/support.service';
import { SupportWidgetStateService } from '../../core/support/support-widget-state.service';
import { AccountStore } from '../../core/trading/account.store';
import { Router } from '@angular/router';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-support-widget',
  imports: [DatePipe, FormsModule, IconComponent],
  templateUrl: './support-widget.component.html',
  styleUrl: './support-widget.component.scss',
})
export class SupportWidgetComponent {
  readonly embedded = input(false);
  private readonly service = inject(SupportService);
  private readonly widgetState = inject(SupportWidgetStateService);
  private readonly accountStore = inject(AccountStore);
  private readonly router = inject(Router);
  readonly isSupport = inject(AuthService).isSupport();

  readonly open = this.widgetState.open;
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly unreadCount = signal(0);
  readonly conversations = signal<SupportConversation[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly selected = computed(() => this.conversations().find((item) => item.id === this.selectedId()) ?? null);

  subject = '';
  body = '';
  reply = '';

  constructor() {
    effect(() => {
      if (this.embedded() || this.open()) this.load();
    });
    effect((onCleanup) => {
      const conversationId = this.selectedId();
      if (!conversationId) return;
      const stream = this.service.connectToConversation(conversationId, (message) => this.appendMessage(conversationId, message));
      onCleanup(() => stream.abort());
    });
  }

  /** Adds a message streamed into a conversation, once, even if the stream repeats it. */
  private appendMessage(conversationId: string, message: SupportMessage): void {
    this.conversations.update((items) => items.map((conversation) => {
      if (conversation.id !== conversationId || conversation.messages.some((item) => item.id === message.id)) {
        return conversation;
      }
      return { ...conversation, updatedAt: message.createdAt, messages: [...conversation.messages, message] };
    }));
  }

  toggle(): void {
    this.open.update((value) => !value);
  }

  close(): void {
    this.open.set(false);
  }

  select(id: string): void {
    this.selectedId.set(id);
    this.reply = '';
  }

  startNewConversation(): void {
    this.selectedId.set(null);
    this.subject = '';
    this.body = '';
    this.error.set('');
  }

  viewCustomerAccount(conversation: SupportConversation, event: Event): void {
    // A real link for the keyboard and screen readers, but the account switch happens in the app
    event.preventDefault();
    if (!this.isSupport || conversation.accountId == null) return;
    this.accountStore.viewAccount(conversation.accountId);
    void this.router.navigate(['/portfolio']);
  }

  submitRequest(): void {
    if (!this.subject.trim() || !this.body.trim()) return;
    this.sending.set(true);
    this.error.set('');
    this.service.createConversation(this.subject.trim(), this.body.trim()).subscribe({
      next: (conversation) => {
        this.conversations.update((items) => [conversation, ...items]);
        this.selectedId.set(conversation.id);
        this.subject = '';
        this.body = '';
        this.sending.set(false);
      },
      error: () => this.fail('Your request could not be sent. Please try again.'),
    });
  }

  sendReply(): void {
    const conversation = this.selected();
    if (!conversation || !this.reply.trim()) return;
    this.sending.set(true);
    this.error.set('');
    this.service.addMessage(conversation.id, this.reply.trim()).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.reply = '';
        this.sending.set(false);
      },
      error: () => this.fail('Your reply could not be sent. Please try again.'),
    });
  }

  closeConversation(): void {
    const conversation = this.selected();
    if (!conversation || !this.isSupport || conversation.status === 'CLOSED') return;
    this.sending.set(true);
    this.error.set('');
    this.service.closeConversation(conversation.id).subscribe({
      next: (closed) => {
        this.replace(closed);
        this.sending.set(false);
      },
      error: () => this.fail('The conversation could not be closed. Please try again.'),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service.getConversations().subscribe({
      next: (conversations) => {
        this.conversations.set(conversations);
        if (!this.selectedId() && conversations.length) this.selectedId.set(conversations[0].id);
        this.loading.set(false);
      },
      error: () => this.fail('Support history is temporarily unavailable.'),
    });
    if (this.isSupport) {
      this.service.getUnreadNotificationCount().subscribe({
        next: ({ unreadCount }) => this.unreadCount.set(unreadCount),
      });
      this.service.markNotificationsRead().subscribe({
        next: () => this.unreadCount.set(0),
      });
    }
  }

  private replace(updated: SupportConversation): void {
    this.conversations.update((items) => items.map((item) => item.id === updated.id ? updated : item));
  }

  private fail(message: string): void {
    this.error.set(message);
    this.loading.set(false);
    this.sending.set(false);
  }
}