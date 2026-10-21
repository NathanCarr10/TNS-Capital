import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { SupportMessage, SupportService } from './support.service';

const MESSAGE: SupportMessage = {
  id: 1, senderEmail: 'support@tns-capital.local', senderRole: 'SUPPORT', body: 'Hello', createdAt: '2026-10-21T10:00:00Z',
};

/** A response whose event stream the test writes to and closes. */
function openStream(): { response: Response; send: (data: string) => void; close: () => void } {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: (c) => (controller = c) });
  const encoder = new TextEncoder();
  return {
    response: new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    send: (data) => controller.enqueue(encoder.encode(`event:message\ndata:${data}\n\n`)),
    close: () => controller.close(),
  };
}

/**
 * Lets the stream reading and reconnecting run. Closing a piped stream is delivered on a task, so
 * this also yields to tasks through a MessageChannel, which the jasmine clock (holding every
 * timer) does not mock.
 */
async function settle(): Promise<void> {
  for (let i = 0; i < 10; i++) {
    await new Promise<void>((resolve) => {
      const channel = new MessageChannel();
      channel.port1.onmessage = () => resolve();
      channel.port2.postMessage(null);
    });
  }
}

describe('SupportService live messages', () => {
  let service: SupportService;
  let auth: jasmine.SpyObj<AuthService>;
  let fetchSpy: jasmine.Spy;
  let stream: AbortController | undefined;

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2026-10-21T10:00:00Z'));
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getAccessToken', 'canRefresh', 'refresh']);
    auth.getAccessToken.and.returnValue('token-1');
    auth.canRefresh.and.returnValue(true);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: auth }],
    });
    service = TestBed.inject(SupportService);
    fetchSpy = spyOn(window, 'fetch');
  });

  afterEach(() => {
    stream?.abort();
    jasmine.clock().uninstall();
  });

  function authorisation(call: number): string | undefined {
    const init = fetchSpy.calls.argsFor(call)[1] as RequestInit;
    return (init.headers as Record<string, string>)['Authorization'];
  }

  it('delivers messages, and reconnects with the current token when the server ends the stream', async () => {
    const first = openStream();
    const second = openStream();
    fetchSpy.and.returnValues(Promise.resolve(first.response), Promise.resolve(second.response));
    const received: SupportMessage[] = [];

    stream = service.connectToConversation('c-1', (message) => received.push(message));
    await settle();
    first.send(JSON.stringify(MESSAGE));
    await settle();
    expect(received).toEqual([MESSAGE]);

    // The server closes the stream after 15 minutes; by then the app holds a newer token
    jasmine.clock().tick(15 * 60 * 1000);
    auth.getAccessToken.and.returnValue('token-2');
    first.close();
    await settle();

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(fetchSpy.calls.argsFor(1)[0]).toBe('/api/v1/support/conversations/c-1/events');
    expect(authorisation(0)).toBe('Bearer token-1');
    expect(authorisation(1)).toBe('Bearer token-2');
  });

  it('refreshes an expired token once, then reconnects', async () => {
    const live = openStream();
    fetchSpy.and.returnValues(Promise.resolve(new Response(null, { status: 401 })), Promise.resolve(live.response));
    auth.refresh.and.callFake(() => {
      auth.getAccessToken.and.returnValue('token-2');
      return of(undefined);
    });

    stream = service.connectToConversation('c-1', () => undefined);
    await settle();

    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(authorisation(1)).toBe('Bearer token-2');
  });

  it('stops when the refreshed token is refused too, or the refresh fails', async () => {
    fetchSpy.and.callFake(() => Promise.resolve(new Response(null, { status: 401 })));
    auth.refresh.and.returnValue(of(undefined));

    stream = service.connectToConversation('c-1', () => undefined);
    await settle();
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(fetchSpy).toHaveBeenCalledTimes(2);

    fetchSpy.calls.reset();
    auth.refresh.and.returnValue(throwError(() => new Error('refresh refused')));
    stream.abort();
    stream = service.connectToConversation('c-2', () => undefined);
    await settle();
    jasmine.clock().tick(60_000);
    await settle();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('stops without retrying when the stream is refused', async () => {
    fetchSpy.and.callFake(() => Promise.resolve(new Response(null, { status: 403 })));

    stream = service.connectToConversation('c-1', () => undefined);
    await settle();
    jasmine.clock().tick(60_000);
    await settle();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(auth.refresh).not.toHaveBeenCalled();
  });

  it('retries a broken connection after a pause, not straight away', async () => {
    const live = openStream();
    fetchSpy.and.returnValues(Promise.reject(new TypeError('network down')), Promise.resolve(live.response));

    stream = service.connectToConversation('c-1', () => undefined);
    await settle();
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    jasmine.clock().tick(3_000);
    await settle();
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it('stops reconnecting once aborted', async () => {
    const first = openStream();
    fetchSpy.and.returnValue(Promise.resolve(first.response));

    stream = service.connectToConversation('c-1', () => undefined);
    await settle();
    stream.abort();
    await settle();
    jasmine.clock().tick(60_000);
    await settle();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});
