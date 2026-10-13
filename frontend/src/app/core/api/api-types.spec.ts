import { LoginRequest } from '../../api/auth';
import { CashTransactionRequest, PlaceOrderRequest } from '../../api/trading';

// Compile-time checks: each @ts-expect-error must sit on a real type error.
// If a regenerated client stops rejecting one of these, the line no longer
// errors, TypeScript reports an unused directive, and the build fails.
describe('Generated API types', () => {
  it('reject requests that break the contract', () => {
    // @ts-expect-error password is required by LoginRequest
    const missingPassword: LoginRequest = { username: 'admin' };

    // @ts-expect-error amount is a number, not a string
    const stringAmount: CashTransactionRequest = { amount: '100' };

    const badSide: PlaceOrderRequest = {
      accountId: 7,
      symbol: 'AAPL',
      // @ts-expect-error side only accepts the BUY or SELL enum values
      side: 'HOLD',
      quantity: 1,
      idempotencyKey: 'key',
    };

    // @ts-expect-error idempotencyKey is required by PlaceOrderRequest
    const missingKey: PlaceOrderRequest = { accountId: 7, symbol: 'AAPL', side: PlaceOrderRequest.SideEnum.BUY, quantity: 1 };

    expect([missingPassword, stringAmount, badSide, missingKey].length).toBe(4);
  });
});
