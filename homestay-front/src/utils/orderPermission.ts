import { OrderStatus } from "@/types/order";

/**
 * 订单权限检查工具
 * 用于检查用户对订单的操作权限
 */

// 用户角色
export enum UserRole {
  USER = "ROLE_USER", // 普通用户
  HOST = "ROLE_HOST", // 房东
  ADMIN = "ROLE_ADMIN", // 管理员
}

// 订单操作类型
export enum OrderAction {
  VIEW = "view", // 查看订单
  CANCEL = "cancel", // 取消订单
  CONFIRM = "confirm", // 确认订单
  REJECT = "reject", // 拒绝订单
  PAY = "pay", // 支付订单
  CHECK_IN = "checkIn", // 办理入住
  CHECK_OUT = "checkOut", // 办理退房
  REVIEW = "review", // 评价订单
  EDIT = "edit", // 编辑订单
  DISPUTE = "dispute", // 发起争议
  RETRY_REFUND = "retryRefund", // 重试退款
}

// 用户身份类型
export type UserIdentity = {
  userId: number;
  role: string;
};

// 订单基本信息类型
export type OrderBasicInfo = {
  id: number;
  userId?: number; // 用户ID
  hostId?: number; // 房东ID
  status: string;
};

/**
 * 检查用户是否是订单的所有者
 * @param order 订单信息
 * @param userId 用户ID
 */
export function isOrderOwner(order: OrderBasicInfo, userId: number): boolean {
  return order.userId === userId;
}

/**
 * 检查用户是否是订单对应房源的房东
 * @param order 订单信息
 * @param userId 用户ID
 */
export function isOrderHost(order: OrderBasicInfo, userId: number): boolean {
  return order.hostId === userId;
}

/**
 * 检查用户是否是管理员
 * @param userRole 用户角色
 */
export function isAdmin(userRole: string): boolean {
  return userRole === UserRole.ADMIN;
}

/**
 * 检查用户是否有订单的访问权限
 * @param order 订单信息
 * @param user 用户身份
 */
export function canAccessOrder(order: OrderBasicInfo, user: UserIdentity): boolean {
  // 管理员可以访问所有订单
  if (isAdmin(user.role)) {
    return true;
  }

  // 用户可以访问自己的订单
  if (user.role === UserRole.USER && isOrderOwner(order, user.userId)) {
    return true;
  }

  // 房东可以访问自己房源的订单
  if (user.role === UserRole.HOST && isOrderHost(order, user.userId)) {
    return true;
  }

  return false;
}

/**
 * 基于状态的操作权限检查
 * @param action 操作类型
 * @param orderStatus 订单状态
 * @param userRole 用户角色
 */
export function canPerformActionOnStatus(
  action: OrderAction,
  orderStatus: string,
  userRole: string
): boolean {
  // 管理员可以执行任何操作
  if (isAdmin(userRole)) {
    return true;
  }

  // 根据角色和订单状态检查权限
  switch (action) {
    case OrderAction.CANCEL:
      if (userRole === UserRole.USER) {
        // 用户只能取消待确认和已确认但未支付的订单
        return [OrderStatus.PENDING, OrderStatus.CONFIRMED].includes(orderStatus as OrderStatus);
      } else if (userRole === UserRole.HOST) {
        // 房东可以取消待确认、已支付、待入住的订单（已支付会进入退款流程）
        return [OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.READY_FOR_CHECKIN].includes(
          orderStatus as OrderStatus
        );
      }
      break;

    case OrderAction.CONFIRM:
      // 只有房东可以确认订单，且只能确认待确认的订单
      return userRole === UserRole.HOST && orderStatus === OrderStatus.PENDING;

    case OrderAction.REJECT:
      // 只有房东可以拒绝订单，且只能拒绝待确认的订单
      return userRole === UserRole.HOST && orderStatus === OrderStatus.PENDING;

    case OrderAction.PAY:
      // 只有用户可以支付订单，且只能支付已确认的订单
      return userRole === UserRole.USER && orderStatus === OrderStatus.CONFIRMED;

    case OrderAction.CHECK_IN:
      // 房东或房客可以办理入住，订单状态需为已支付或待入住
      return (
        (userRole === UserRole.HOST || userRole === UserRole.USER) &&
        [OrderStatus.PAID, OrderStatus.READY_FOR_CHECKIN].includes(orderStatus as OrderStatus)
      );

    case OrderAction.CHECK_OUT:
      // 只有房东可以办理退房，且只能处理已入住的订单
      return userRole === UserRole.HOST && orderStatus === OrderStatus.CHECKED_IN;

    case OrderAction.REVIEW:
      // 只有用户可以评价订单，且只能评价已完成的订单
      return userRole === UserRole.USER && orderStatus === OrderStatus.COMPLETED;

    case OrderAction.EDIT:
      // 订单一旦创建就不能编辑
      return false;

    case OrderAction.DISPUTE:
      // 房东或用户可以对退款中的订单发起争议
      return (
        (userRole === UserRole.USER || userRole === UserRole.HOST) &&
        orderStatus === OrderStatus.REFUND_PENDING
      );

    case OrderAction.RETRY_REFUND:
      // 用户可以对退款失败的订单重试退款
      return userRole === UserRole.USER && orderStatus === OrderStatus.REFUND_FAILED;

    case OrderAction.VIEW:
      // 所有人都可以查看订单
      return true;

    default:
      return false;
  }

  return false;
}

/**
 * 综合权限检查函数
 * @param order 订单信息
 * @param user 用户身份
 * @param action 操作类型
 */
export function checkOrderPermission(
  order: OrderBasicInfo,
  user: UserIdentity,
  action: OrderAction
): boolean {
  // 首先检查是否有访问权限
  if (!canAccessOrder(order, user)) {
    return false;
  }

  // 然后检查是否有操作权限
  return canPerformActionOnStatus(action, order.status, user.role);
}

/**
 * 获取用户在界面上能执行的操作列表
 * @param order 订单信息
 * @param user 用户身份
 */
export function getAvailableActions(order: OrderBasicInfo, user: UserIdentity): OrderAction[] {
  const availableActions: OrderAction[] = [OrderAction.VIEW]; // 默认至少可以查看

  // 检查每个可能的操作
  for (const action of Object.values(OrderAction)) {
    if (action !== OrderAction.VIEW && checkOrderPermission(order, user, action)) {
      availableActions.push(action);
    }
  }

  return availableActions;
}
