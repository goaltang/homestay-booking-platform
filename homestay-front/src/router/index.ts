import { createRouter, createWebHistory } from "vue-router";
import Home from "../views/Home.vue";
import { useUserStore } from "../stores/user";
import OrderSubmitSuccess from "../views/order/OrderSubmitSuccess.vue";
import MyOrders from "../views/order/MyOrders.vue";

import UserLayout from "@/layouts/UserLayout.vue";
import HostLayout from "../views/host/HostLayout.vue";

const log = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.log(...args);
};
const warn = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.warn(...args);
};

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: Home,
    },
    {
      path: "/login",
      name: "login",
      component: () => import("../views/Login.vue"),
    },
    {
      path: "/register",
      name: "register",
      component: () => import("../views/Register.vue"),
    },
    {
      path: "/homestays",
      name: "homestays",
      component: () => import("../views/HomestayListView.vue"),
    },
    {
      path: "/map-search",
      name: "map-search",
      component: () => import("../views/MapSearch.vue"),
      meta: { title: "地图找房" },
    },
    {
      path: "/about",
      name: "about",
      component: () => import("../views/About.vue"),
      meta: { title: "关于我们" },
    },
    {
      path: "/homestays/:id",
      name: "homestay-detail",
      component: () => import("../views/HomestayDetail.vue"),
      meta: { title: "房源详情" },
    },
    {
      path: "/profile",
      name: "profile",
      component: () => import("../views/user/Profile.vue"),
      meta: { requiresAuth: true },
    },

    {
      path: "/reset-password",
      name: "reset-password",
      component: () => import("../views/ResetPassword.vue"),
    },
    {
      path: "/forgot-password",
      name: "forgot-password",
      component: () => import("../views/ForgotPassword.vue"),
    },

    // 房东中心路由
    {
      path: "/host",
      component: HostLayout,
      meta: {
        requiresAuth: true,
        title: "房东中心",
        roles: ["ROLE_HOST", "ROLE_LANDLORD"],
      },
      children: [
        {
          path: "",
          name: "HostDashboard",
          component: () => import("../views/host/Dashboard.vue"),
          meta: {
            title: "房东控制台",
            icon: "dashboard",
          },
        },

        {
          path: "homestay",
          name: "HostHomestay",
          component: () => import("../views/host/HomestayManage.vue"),
          meta: {
            title: "房源管理",
            icon: "home",
          },
        },
        {
          path: "homestay/create",
          name: "HostHomestayCreate",
          component: () => import("../views/host/HomestayForm.vue"),
          meta: {
            title: "添加新房源",
            icon: "plus",
            activeMenu: "/host/homestay",
          },
        },
        {
          path: "homestay/edit/:id",
          name: "HostHomestayEdit",
          component: () => import("../views/host/HomestayForm.vue"),
          meta: {
            title: "编辑房源",
            activeMenu: "/host/homestay",
          },
        },
        {
          path: "calendar",
          name: "HostCalendar",
          component: () => import("../views/host/CalendarManage.vue"),
          meta: {
            title: "日历管理",
            icon: "calendar",
          },
        },
        {
          path: "orders",
          name: "HostOrders",
          component: () => import("../views/host/OrderManage.vue"),
          meta: {
            title: "订单管理",
            icon: "list",
          },
        },
        {
          path: "orders/:id",
          name: "HostOrderDetail",
          component: () => import("../views/host/HostOrderDetail.vue"),
          meta: {
            title: "订单详情",
            activeMenu: "/host/orders",
          },
        },
        {
          path: "earnings",
          name: "HostEarnings",
          component: () => import("../views/host/EarningManage.vue"),
          meta: {
            title: "收益管理",
            icon: "money",
          },
        },
        {
          path: "withdrawal",
          name: "HostWithdrawal",
          component: () => import("../views/host/Withdrawal.vue"),
          meta: {
            title: "提现管理",
            icon: "wallet",
            activeMenu: "/host/earnings",
          },
        },
        {
          path: "reviews",
          name: "HostReviews",
          component: () => import("../views/host/ReviewManage.vue"),
          meta: {
            title: "评价管理",
            icon: "star",
          },
        },
        {
          path: "profile",
          name: "HostProfile",
          component: () => import("../views/host/ProfileManage.vue"),
          meta: {
            title: "个人资料",
            icon: "user",
          },
        },
        {
          path: "notifications",
          name: "HostNotifications",
          component: () => import("@/views/host/NotificationManage.vue"),
          meta: {
            title: "通知管理",
            icon: "bell",
            activeMenu: "/host/notifications",
          },
        },
        {
          path: "messages",
          name: "HostMessages",
          component: () => import("../views/host/MessageCenter.vue"),
          meta: {
            title: "消息中心",
            icon: "chat",
          },
        },
        {
          path: "promotions",
          name: "HostPromotions",
          component: () => import("../views/host/HostPromotionManage.vue"),
          meta: {
            title: "营销活动",
            icon: "ticket",
          },
        },
        {
          path: "promotions/stats",
          name: "HostPromotionStats",
          component: () => import("../views/host/HostPromotionStats.vue"),
          meta: {
            title: "营销数据",
            icon: "trend-charts",
            activeMenu: "/host/promotions",
          },
        },
      ],
    },
    {
      path: "/host/onboarding",
      name: "HostOnboarding",
      component: () => import("../views/host/HostOnboarding.vue"),
      meta: {
        title: "房东信息完善",
        requiresAuth: true
      },
    },
    {
      path: "/order/confirm",
      name: "OrderConfirm",
      component: () => import("../views/order/OrderConfirm.vue"),
      meta: { title: "确认订单", requiresAuth: true },
    },
    {
      path: "/orders/:id",
      name: "OrderDetail",
      component: () => import("../views/order/OrderDetail.vue"),
      meta: { title: "订单详情", requiresAuth: true },
    },
    {
      path: "/orders/:id/pay",
      name: "OrderPay",
      component: () => import("../views/order/OrderPay.vue"),
      meta: { title: "订单支付", requiresAuth: true },
    },
    {
      path: "/orders/:id/pay-success",
      name: "OrderPaySuccess",
      component: () => import("../views/order/OrderPaySuccess.vue"),
      meta: { title: "支付成功", requiresAuth: true },
    },
    {
      path: "/orders/submit-success/:id",
      name: "OrderSubmitSuccess",
      component: OrderSubmitSuccess,
      meta: { requiresAuth: true },
    },

    // 用户中心路由
    {
      path: "/user",
      component: UserLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: "profile",
          name: "UserProfile",
          component: () => import("../views/user/Profile.vue"),
          meta: { title: "个人中心" },
        },
        {
          path: "favorites",
          name: "UserFavorites",
          component: () => import("../views/user/Favorites.vue"),
          meta: { title: "我的收藏" },
        },
        {
          path: "reviews",
          name: "UserReviews",
          component: () => import("../views/user/MyReviews.vue"),
          meta: { title: "我的评价" },
        },
        {
          path: "bookings",
          name: "UserBookings",
          component: MyOrders,
          meta: { title: "我的订单" },
        },
        {
          path: "notifications",
          name: "UserNotifications",
          component: () => import("@/views/user/NotificationCenter.vue"),
          meta: { title: "通知中心" },
        },
        {
          path: "invite",
          name: "UserInvite",
          component: () => import("@/views/user/MyInvite.vue"),
          meta: { title: "我的邀请" },
        },
        {
          path: "coupons",
          name: "UserCoupons",
          component: () => import("@/views/user/MyCoupons.vue"),
          meta: { title: "我的优惠券" },
        },
        {
          path: "companions",
          name: "UserCompanions",
          component: () => import("@/views/user/Companions.vue"),
          meta: { title: "常用入住人" },
        },
      ],
    },
  ],
});

/** 检查用户角色是否匹配路由要求的角色 */
function checkRoleMatch(userRole: string, requiredRole: string): boolean {
  const user = userRole.toUpperCase();
  const req = requiredRole.toUpperCase();

  if (user === req) return true;
  if (user === req.replace("ROLE_", "")) return true;
  if (`ROLE_${user}` === req) return true;

  // ROLE_LANDLORD 和 ROLE_HOST 互相兼容
  if (
    (user === "ROLE_LANDLORD" && req === "ROLE_HOST") ||
    (user === "ROLE_HOST" && req === "ROLE_LANDLORD")
  ) {
    return true;
  }

  return false;
}

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore();

  const isAuthenticated = userStore.isAuthenticated;
  const userRole = userStore.normalizedRole;
  const requiredRoles = Array.isArray(to.meta.roles) ? to.meta.roles : null;

  log("路由导航:", { from: from.path, to: to.path, meta: to.meta });

  // 需要认证但未登录
  if (to.meta.requiresAuth && !isAuthenticated) {
    warn("需要登录权限，重定向到登录页");
    const query: Record<string, string> = { redirect: to.fullPath };
    if (!from.name || from.name === "home") {
      query.message = "请先登录后再访问此页面";
    }
    next({ name: "login", query });
    return;
  }

  // 登录状态存在但缺少角色信息（数据异常）
  if (requiredRoles && isAuthenticated && !userRole) {
    warn("登录状态缺少角色信息");
    userStore.logout();
    next({
      name: "login",
      query: {
        redirect: to.fullPath,
        message: "登录状态异常，请重新登录",
      },
    });
    return;
  }

  // 角色权限检查
  if (requiredRoles && isAuthenticated) {
    const hasRole = requiredRoles.some((role) => checkRoleMatch(userRole, role));
    if (!hasRole) {
      warn("用户角色不符合要求:", { userRole, requiredRoles });
      next({ name: "home", query: { error: "您没有权限访问此页面" } });
      return;
    }
  }

  // 已登录用户访问登录/注册页
  if (isAuthenticated && (to.name === "login" || to.name === "register")) {
    log("已登录用户尝试访问登录/注册页面，重定向到首页");
    next({ name: "home" });
    return;
  }

  log("导航允许通过");
  next();
});

export default router;
