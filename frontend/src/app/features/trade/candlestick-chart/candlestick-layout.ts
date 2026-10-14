import { PriceBar } from '../../../core/trading/trading.models';

export const PRICE_AXIS_WIDTH = 64;
export const TIME_AXIS_HEIGHT = 24;
const TOP_PADDING = 12;
// Share of the plot under the candles given to volume bars
const VOLUME_SHARE = 0.18;
const VOLUME_GAP = 8;
const MAX_BODY_WIDTH = 18;
const MIN_TIME_TICK_SPACING = 110;
/** Narrowest slot a bar gets; charts too narrow for every bar show the most recent ones. */
export const MIN_SLOT = 4;

export interface Candle {
  bar: PriceBar;
  /** Centre of the candle, in pixels from the chart's left edge. */
  x: number;
  direction: 'up' | 'down';
  wickTop: number;
  wickBottom: number;
  bodyTop: number;
  bodyHeight: number;
  closeY: number;
  volumeTop: number;
}

export interface Tick {
  value: number;
  /** Pixel position along the tick's axis. */
  position: number;
}

export interface CandlestickLayout {
  candles: Candle[];
  /** Horizontal space given to each bar. */
  slot: number;
  bodyWidth: number;
  plotRight: number;
  priceTop: number;
  priceBottom: number;
  volumeBottom: number;
  priceTicks: Tick[];
  /** Ticks along the time axis; the value is the index of the bar they label. */
  timeTicks: Tick[];
  /** SVG path data for each overlay line, e.g. a moving average; empty where it has no values. */
  overlayPaths: string[];
}

/**
 * Simple moving average of the closes over the given number of bars; null for the
 * bars before enough history has built up.
 */
export function movingAverage(bars: PriceBar[], period: number): (number | null)[] {
  let sum = 0;
  return bars.map((bar, index) => {
    sum += bar.close;
    if (index >= period) {
      sum -= bars[index - period].close;
    }
    return index >= period - 1 ? sum / period : null;
  });
}

/**
 * Positions OHLC bars as candlesticks in a chart of the given size, with
 * the price axis on the right and volume bars under the candles. Overlays are
 * lines with one value per bar, drawn on the price scale.
 */
export function layoutCandlesticks(
  bars: PriceBar[],
  width: number,
  height: number,
  overlays: (number | null)[][] = []
): CandlestickLayout | null {
  const plotRight = width - PRICE_AXIS_WIDTH;
  const volumeBottom = height - TIME_AXIS_HEIGHT;
  const volumeHeight = (volumeBottom - TOP_PADDING) * VOLUME_SHARE;
  const priceTop = TOP_PADDING;
  const priceBottom = volumeBottom - volumeHeight - VOLUME_GAP;
  if (bars.length === 0 || plotRight <= 0 || priceBottom <= priceTop) {
    return null;
  }

  const [min, max] = priceDomain(bars, overlays);
  const priceY = (price: number) => priceBottom - ((price - min) / (max - min)) * (priceBottom - priceTop);
  const maxVolume = Math.max(...bars.map((bar) => bar.volume));
  const slot = plotRight / bars.length;
  const bodyWidth = Math.max(1, Math.min(MAX_BODY_WIDTH, slot * 0.7));

  const candles = bars.map((bar, index): Candle => {
    const bodyTop = priceY(Math.max(bar.open, bar.close));
    return {
      bar,
      x: slot * (index + 0.5),
      direction: bar.close >= bar.open ? 'up' : 'down',
      wickTop: priceY(bar.high),
      wickBottom: priceY(bar.low),
      bodyTop,
      // A doji, where open equals close, still shows as a line
      bodyHeight: Math.max(1, priceY(Math.min(bar.open, bar.close)) - bodyTop),
      closeY: priceY(bar.close),
      volumeTop: maxVolume > 0 ? volumeBottom - (bar.volume / maxVolume) * volumeHeight : volumeBottom,
    };
  });

  const priceStep = niceStep(max - min, 5);
  const priceTicks: Tick[] = [];
  for (let value = Math.ceil(min / priceStep) * priceStep; value <= max; value += priceStep) {
    priceTicks.push({ value, position: priceY(value) });
  }

  // Spaced so the labels never overlap, and clear of both edges
  const every = Math.max(1, Math.ceil(bars.length / Math.max(1, Math.floor(plotRight / MIN_TIME_TICK_SPACING))));
  const timeTicks: Tick[] = [];
  for (let index = 0; index < bars.length; index += every) {
    const position = candles[index].x;
    if (position >= MIN_TIME_TICK_SPACING / 2 && position + MIN_TIME_TICK_SPACING / 2 <= plotRight) {
      timeTicks.push({ value: index, position });
    }
  }

  const overlayPaths = overlays.map((values) =>
    values
      .map((value, index) => (value == null ? null : `${candles[index].x.toFixed(1)},${priceY(value).toFixed(1)}`))
      .filter((point): point is string => point !== null)
      .map((point, index) => (index === 0 ? 'M' : 'L') + point)
      .join('')
  );

  return { candles, slot, bodyWidth, plotRight, priceTop, priceBottom, volumeBottom, priceTicks, timeTicks, overlayPaths };
}

// The bars' and overlays' price range with some headroom, so no wick or line touches the edge
function priceDomain(bars: PriceBar[], overlays: (number | null)[][]): [number, number] {
  const overlayValues = overlays.flat().filter((value): value is number => value != null);
  const low = Math.min(...bars.map((bar) => bar.low), ...overlayValues);
  const high = Math.max(...bars.map((bar) => bar.high), ...overlayValues);
  const padding = high > low ? (high - low) * 0.05 : Math.max(Math.abs(high) * 0.01, 0.01);
  return [low - padding, high + padding];
}

/** A round step (1, 2 or 5 times a power of ten) giving about the target number of ticks over the range. */
export function niceStep(range: number, target: number): number {
  const raw = range / target;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const normalized = raw / magnitude;
  if (normalized < 1.5) {
    return magnitude;
  }
  if (normalized < 3) {
    return 2 * magnitude;
  }
  return normalized < 7 ? 5 * magnitude : 10 * magnitude;
}
