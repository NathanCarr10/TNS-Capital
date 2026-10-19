import { EquityPoint } from '../../../api/strategy';
import { niceStep } from '../../trade/candlestick-chart/candlestick-layout';

export interface ChartBox {
  width: number;
  height: number;
  padding: { top: number; right: number; bottom: number; left: number };
}

export interface CurvePoint {
  x: number;
  strategyY: number;
  buyAndHoldY: number;
  point: EquityPoint;
}

export interface EquityCurveLayout {
  points: CurvePoint[];
  strategyLine: string;
  buyAndHoldLine: string;
  valueTicks: { value: number; y: number }[];
  timeTicks: CurvePoint[];
  plotLeft: number;
  plotRight: number;
  plotTop: number;
  plotBottom: number;
}

const MIN_TIME_TICK_SPACING = 110;

/** Lays out both portfolios' value over time on one value scale; null when there is nothing to draw. */
export function layoutEquityCurve(curve: EquityPoint[], box: ChartBox): EquityCurveLayout | null {
  const plotLeft = box.padding.left;
  const plotRight = box.width - box.padding.right;
  const plotTop = box.padding.top;
  const plotBottom = box.height - box.padding.bottom;
  if (curve.length < 2 || plotRight <= plotLeft) {
    return null;
  }

  const values = curve.flatMap((point) => [point.strategy ?? 0, point.buyAndHold ?? 0]);
  const low = Math.min(...values);
  const high = Math.max(...values);
  const margin = high > low ? (high - low) * 0.06 : Math.max(high * 0.01, 1);
  const [min, max] = [low - margin, high + margin];
  const x = (index: number) => plotLeft + (index / (curve.length - 1)) * (plotRight - plotLeft);
  const y = (value: number) => plotBottom - ((value - min) / (max - min)) * (plotBottom - plotTop);

  const points = curve.map((point, index) => ({
    x: x(index),
    strategyY: y(point.strategy ?? 0),
    buyAndHoldY: y(point.buyAndHold ?? 0),
    point,
  }));
  const path = (pick: (p: CurvePoint) => number) =>
    points.map((p, index) => `${index === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${pick(p).toFixed(1)}`).join('');

  const step = niceStep(max - min, 4);
  const valueTicks = [];
  for (let value = Math.ceil(min / step) * step; value <= max; value += step) {
    valueTicks.push({ value, y: y(value) });
  }
  const every = Math.max(
    1,
    Math.ceil(curve.length / Math.max(1, Math.floor((plotRight - plotLeft) / MIN_TIME_TICK_SPACING)))
  );
  const timeTicks = points.filter((_, index) => index % every === 0 && index > 0 && index < points.length - 1);

  return {
    points,
    strategyLine: path((p) => p.strategyY),
    buyAndHoldLine: path((p) => p.buyAndHoldY),
    valueTicks,
    timeTicks,
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
  };
}
