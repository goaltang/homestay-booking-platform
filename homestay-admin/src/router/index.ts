import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    redirect: "/dashboard",
  },
  {
    path: "/",
    name: "Home",
    component: () => import("@/views/layout/index.vue"),
    children: [
      {
        path: "/dashboard",
        name: "dashboard",
        meta: {
          title: "系统首页",
          requiresAuth: true,
        },
        component: () => import("@/views/dashboard/index.vue"),
      },
      {
        path: "/homestays",
        name: "homestays",
        meta: {
          title: "房源管理",
          requiresAuth: true,
        },
        component: () => import("@/views/homestay/list.vue"),
      },
      {
        path: "/orders",
        name: "orders",
        meta: {
          title: "订单管理",
          requiresAuth: true,
        },
        component: () => import("@/views/order/list.vue"),
      },
      {
        path: "/users",
        name: "users",
        meta: {
          title: "用户管理",
          requiresAuth: true,
        },
        component: () => import("@/views/user/list.vue"),
      },
      {
        path: "/verifications",
        name: "verifications",
        meta: {
          title: "身份认证审核",
          requiresAuth: true,
        },
        component: () => import("@/views/user/verification.vue"),
      },
      {
        path: "/reviews",
        name: "reviews",
        meta: {
          title: "评价管理",
          requiresAuth: true,
        },
        component: () => import("@/views/review/list.vue"),
      },
      {
        path: "/statistics",
        name: "statistics",
        meta: {
          title: "统计分析",
          requiresAuth: true,
        },
        component: () => import("@/views/statistics/index.vue"),
      },
      {
        path: "/types",
        name: "types",
        meta: {
          title: "房源类型管理",
          requiresAuth: true,
        },
        component: () => import("@/views/TypeManage.vue"),
      },
      {
        path: "/amenities",
        name: "amenities",
        redirect: "/amenities/manage",
        meta: {
          title: "设施管理",
          requiresAuth: true,
        },
      },
      {
        path: "/amenities/manage",
        name: "amenitiesManage",
        meta: {
          title: "设施列表",
          requiresAuth: true,
        },
        component: () => import("@/views/amenities/AmenitiesManage.vue"),
      },
      {
        path: "/amenities/categories",
        name: "amenitiesCategories",
        meta: {
          title: "设施分类",
          requiresAuth: true,
        },
        component: () => import("@/views/amenities/CategoryManage.vue"),
      },
      {
        path: "/homestays/edit/:id",
        name: "homestaysEdit",
        meta: {
          title: "编辑房源",
          requiresAuth: true,
        },
        component: () => import("@/views/homestay/edit.vue"),
      },
      {
        path: "/homestays/add",
        name: "homestaysAdd",
        meta: {
          title: "新增房源",
          requiresAuth: true,
        },
        component: () =>
          import("@/views/homestay/edit.vue").catch((error) => {
            console.error("加载 add (edit.vue) 组件失败:", error);
            return import("@/views/error/404.vue");
          }),
      },
      {
        path: "/groups",
        name: "groupManage",
        meta: {
          title: "房源分组管理",
          requiresAuth: true,
        },
        component: () => import("@/views/homestay/GroupManage.vue"),
      },
      {
        path: "/notifications",
        name: "notifications",
        meta: {
          title: "通知中心",
          requiresAuth: true,
        },
        component: () => import("@/views/notifications/index.vue"),
      },
      {
        path: "/broadcast",
        name: "broadcast",
        meta: {
          title: "广播管理",
          requiresAuth: true,
        },
        component: () => import("@/views/broadcast/index.vue"),
      },
      {
        path: "/audit/workbench",
        name: "auditWorkbench",
        meta: {
          title: "审核工作台",
          requiresAuth: true,
        },
        component: () => import("@/views/audit/workbench.vue"),
      },
      {
        path: "/audit/history",
        name: "auditHistory",
        meta: {
          title: "审核历史",
          requiresAuth: true,
        },
        component: () => import("@/views/audit/history.vue"),
      },
      {
        path: "/audit/statistics",
        name: "auditStatistics",
        meta: {
          title: "审核统计",
          requiresAuth: true,
        },
        component: () => import("@/views/audit/statistics.vue"),
      },
      {
        path: "/marketing/campaigns",
        name: "marketingCampaigns",
        meta: {
          title: "活动管理",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/CampaignList.vue"),
      },
      {
        path: "/marketing/coupons",
        name: "marketingCoupons",
        meta: {
          title: "优惠券模板",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/CouponTemplateList.vue"),
      },
      {
        path: "/marketing/dashboard",
        name: "marketingDashboard",
        meta: {
          title: "营销报表",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/MarketingDashboard.vue"),
      },
      {
        path: "/marketing/batch-issue",
        name: "marketingBatchIssue",
        meta: {
          title: "批量发券",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/BatchIssueManage.vue"),
      },
      {
        path: "/marketing/referral",
        name: "marketingReferral",
        meta: {
          title: "邀请裂变",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/ReferralManage.vue"),
      },
      {
        path: "/marketing/funnel",
        name: "marketingFunnel",
        meta: {
          title: "转化漏斗",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/CouponFunnel.vue"),
      },
      {
        path: "/marketing/ab-test",
        name: "marketingAbTest",
        meta: {
          title: "AB测试",
          requiresAuth: true,
        },
        component: () => import("@/views/marketing/AbTestManage.vue"),
      },
      {
        path: "/pricing/rules",
        name: "pricingRules",
        meta: {
          title: "价格规则",
          requiresAuth: true,
        },
        component: () => import("@/views/pricing/PricingRuleManage.vue"),
      },
      {
        path: "/pricing/holidays",
        name: "pricingHolidays",
        meta: {
          title: "节假日管理",
          requiresAuth: true,
        },
        component: () => import("@/views/pricing/HolidayManage.vue"),
      },
      {
        path: "/system/config",
        name: "systemConfig",
        meta: {
          title: "系统配置",
          requiresAuth: true,
        },
        component: () => import("@/views/system/Config.vue"),
      },
      {
        path: "/system/logs",
        name: "operationLogs",
        meta: {
          title: "操作日志",
          requiresAuth: true,
        },
        component: () => import("@/views/system/OperationLog.vue"),
      },
      {
        path: "/system/login-logs",
        name: "loginLogs",
        meta: {
          title: "登录日志",
          requiresAuth: true,
        },
        component: () => import("@/views/system/LoginLog.vue"),
      },
      {
        path: "/system/announcements",
        name: "announcements",
        meta: {
          title: "公告管理",
          requiresAuth: true,
        },
        component: () => import("@/views/system/Announcement.vue"),
      },
      {
        path: "/system/banners",
        name: "banners",
        meta: {
          title: "Banner 管理",
          requiresAuth: true,
        },
        component: () => import("@/views/system/Banner.vue"),
      },
      {
        path: "/system/disputes",
        name: "disputeManage",
        meta: {
          title: "争议管理",
          requiresAuth: true,
        },
        component: () => import("@/views/system/DisputeManage.vue"),
      },
    ],
  },
  {
    path: "/login",
    name: "Login",
    meta: {
      title: "登录",
    },
    component: () => import("@/views/login/index.vue"),
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    meta: {
      title: "页面不存在",
    },
    component: () => import("@/views/error/404.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.onError((error) => {
  console.error("路由组件加载失败:", error);
});

router.beforeEach((to, _from, next) => {
  try {
    const title = to.meta?.title as string | undefined;
    if (title) {
      document.title = title;
    }

    const token = localStorage.getItem("homestay_admin_token") || localStorage.getItem("token");
    if (to.meta?.requiresAuth && !token) {
      next("/login");
    } else {
      next();
    }
  } catch (error) {
    console.error("路由导航错误:", error);
    next("/login"); // 出错时导航到登录页
  }
});

export default router;
