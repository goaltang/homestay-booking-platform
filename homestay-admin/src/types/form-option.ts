/**
 * table-edit 组件使用的表单选项结构（兼容旧组件）
 */
export interface FormOption {
  /** 表单字段列表 */
  list: FormOptionItemEdit[];
  /** 标签宽度 */
  labelWidth?: string;
  /** 每列占用的栅格数 */
  span?: number;
}

/**
 * table-edit 组件的单个表单字段配置
 */
export interface FormOptionItemEdit {
  /** 表单标签 */
  label: string;
  /** 对应 form 中的属性名 */
  prop: string;
  /** 表单类型 */
  type: "input" | "number" | "select" | "date" | "switch" | "upload" | string;
  /** 占位文本 */
  placeholder?: string;
  /** 是否禁用 */
  disabled?: boolean;
  /** 是否必填 */
  required?: boolean;
  /** select / switch 等选项 */
  opts?: Array<{ label: string; value: any }>;
  /** 日期格式化字符串 */
  format?: string;
  /** switch 激活值 */
  activeValue?: any;
  /** switch 非激活值 */
  inactiveValue?: any;
  /** switch 激活文本 */
  activeText?: string;
  /** switch 非激活文本 */
  inactiveText?: string;
}

/**
 * 通用表格筛选组件支持的表单类型
 */
export type FormOptionType =
  | "input"
  | "select"
  | "date"
  | "datetime"
  | "daterange"
  | "datetimerange"
  | "number"
  | "number-range"
  | "switch"
  | "cascader"
  | "remote-select";

/**
 * select / cascader / remote-select 的选项结构
 */
export interface FormOptionItem {
  label: string;
  value: any;
  children?: FormOptionItem[];
}

/**
 * table-search 组件的单个筛选项配置
 */
export interface FormOptionList {
  /** 表单标签 */
  label: string;
  /** 对应 query 中的属性名 */
  prop: string;
  /** 表单类型 */
  type: FormOptionType;
  /** 占位文本 */
  placeholder?: string;
  /** 是否禁用 */
  disabled?: boolean;
  /** select / cascader / switch 等选项 */
  opts?: FormOptionItem[];
  /** 日期格式化字符串（date / datetime / daterange 等有效） */
  format?: string;
  /** 日期范围/数字范围的分隔符文本（默认 "至"） */
  rangeSeparator?: string;
  /** 远程搜索的 API 方法（remote-select 有效） */
  remoteMethod?: (query: string) => Promise<FormOptionItem[]>;
  /** 级联选择器的 props 配置（cascader 有效） */
  cascaderProps?: Record<string, any>;
  /** 数字输入的最小值（number / number-range 有效） */
  min?: number;
  /** 数字输入的最大值（number / number-range 有效） */
  max?: number;
  /** 数字输入的精度（number / number-range 有效） */
  precision?: number;
  /** 是否多选（select / remote-select 有效） */
  multiple?: boolean;
  /** 是否可清空 */
  clearable?: boolean;
}
