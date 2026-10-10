/**
 * 格式化日期为YYYY-MM-DD格式
 * @param date 要格式化的日期对象
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: Date): string {
  if (!date) return "";

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

/**
 * 格式化日期范围
 * @param startDate 开始日期
 * @param endDate 结束日期
 * @returns 格式化后的日期范围字符串
 */
export function formatDateRange(startDate: string | Date, endDate: string | Date): string {
  if (!startDate || !endDate) return "";

  // 如果输入是字符串，尝试转换为Date对象
  const start = typeof startDate === "string" ? new Date(startDate) : startDate;
  const end = typeof endDate === "string" ? new Date(endDate) : endDate;

  // 格式化为 MM月DD日 的形式
  const startMonth = start.getMonth() + 1;
  const startDay = start.getDate();
  const endMonth = end.getMonth() + 1;
  const endDay = end.getDate();

  return `${startMonth}月${startDay}日 - ${endMonth}月${endDay}日`;
}

/**
 * 计算两个日期之间的天数
 * @param startDate 开始日期
 * @param endDate 结束日期
 * @returns 天数
 */
export function getDaysBetween(startDate: Date, endDate: Date): number {
  if (!startDate || !endDate) return 0;

  // 将时间设置为当天的0点，以避免时区和小时计算的问题
  const start = new Date(startDate);
  start.setHours(0, 0, 0, 0);

  const end = new Date(endDate);
  end.setHours(0, 0, 0, 0);

  // 计算相差的毫秒数并转换为天数
  const diffTime = Math.abs(end.getTime() - start.getTime());
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  return diffDays;
}
