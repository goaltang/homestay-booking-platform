/**
 * API 入口 - 统一导出 request 实例
 *
 * 所有 API 调用统一通过此模块导入，底层复用 utils/request.ts 的 axios 实例，
 * 确保拦截器（认证、错误处理、日志）行为一致。
 */
export { default } from '@/utils/request'
export { default as request } from '@/utils/request'
