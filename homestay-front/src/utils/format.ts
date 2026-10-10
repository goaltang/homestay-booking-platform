/**
 * 格式化日期
 * @param date 日期字符串或Date对象
 * @param format 格式化模板，例如 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: string | Date, format: string = "YYYY-MM-DD"): string {
  if (!date) return "";

  const d = typeof date === "string" ? new Date(date) : date;

  if (isNaN(d.getTime())) {
    console.warn("Invalid date:", date);
    return "";
  }

  const year = d.getFullYear();
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const hours = d.getHours();
  const minutes = d.getMinutes();
  const seconds = d.getSeconds();

  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);

  return format
    .replace("YYYY", `${year}`)
    .replace("MM", pad(month))
    .replace("DD", pad(day))
    .replace("HH", pad(hours))
    .replace("mm", pad(minutes))
    .replace("ss", pad(seconds));
}

/**
 * 格式化金额
 * @param amount 金额数值
 * @param currency 货币符号，默认为 '¥'
 * @param decimals 小数位数，默认为 2
 * @returns 格式化后的金额字符串
 */
export function formatCurrency(
  amount: number,
  currency: string = "¥",
  decimals: number = 2
): string {
  if (typeof amount !== "number") return "";

  return `${currency}${amount.toFixed(decimals)}`;
}

/**
 * 格式化文件大小
 * @param bytes 文件大小（字节）
 * @returns 格式化后的文件大小字符串
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";

  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

/**
 * 截断文本
 * @param text 原始文本
 * @param length 最大长度
 * @param suffix 后缀，默认为 '...'
 * @returns 截断后的文本
 */
export function truncateText(text: string, length: number, suffix: string = "..."): string {
  if (!text) return "";

  if (text.length <= length) return text;

  return text.substring(0, length) + suffix;
}
