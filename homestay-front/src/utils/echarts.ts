/**
 * echarts 按需引入封装
 * 只注册项目实际用到的图表类型和组件，避免全量引入（全量约 1MB）。
 */
import * as echarts from "echarts/core";
import { BarChart, PieChart } from "echarts/charts";
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
]);

export default echarts;
export type { EChartsType } from "echarts/core";
