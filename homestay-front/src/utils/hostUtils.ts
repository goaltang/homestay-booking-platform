import type { HostDTO, HostDisplayInfo, HostStatistics } from "@/types/host";

/**
 * 语言代码映射表
 */
const LANGUAGE_MAP: Record<string, string> = {
  CHINESE: "中文",
  ENGLISH: "英语",
  FRENCH: "法语",
  SPANISH: "西班牙语",
  GERMAN: "德语",
  JAPANESE: "日语",
  KOREAN: "韩语",
  ITALIAN: "意大利语",
  RUSSIAN: "俄语",
  PORTUGUESE: "葡萄牙语",
  ARABIC: "阿拉伯语",
  THAI: "泰语",
  VIETNAMESE: "越南语",
  OTHER: "其他语言",
};

/**
 * 房东信息工具类
 * 统一处理房东数据的转换、格式化和显示逻辑
 */
export class HostUtils {
  /**
   * 获取房东显示名称
   * 优先级：realName > nickname > username
   */
  static getDisplayName(host: HostDTO | null): string {
    if (!host) return "房东";

    return host.realName?.trim() || host.nickname?.trim() || host.username?.trim() || "房东";
  }

  /**
   * 获取房东显示头像
   * 如果没有头像则返回默认头像
   */
  static getDisplayAvatar(host: HostDTO | null): string {
    return host?.avatar || "/default-avatar.png";
  }

  /**
   * 获取房东显示评分
   * 优先使用rating字段，然后尝试解析hostRating字段
   */
  static getDisplayRating(host: HostDTO | null): number {
    if (!host) return 0;

    // 优先使用 rating 字段（数值类型）
    if (host.rating !== undefined && host.rating !== null && host.rating > 0) {
      return Number(host.rating);
    }

    // 尝试解析 hostRating 字段（字符串类型）
    if (host.hostRating) {
      const parsed = parseFloat(host.hostRating);
      return isNaN(parsed) ? 0 : parsed;
    }

    return 0;
  }

  /**
   * 判断房东是否已认证
   * 基于多个字段综合判断
   */
  static isVerified(host: HostDTO | null): boolean {
    if (!host) return false;

    return !!(host.realName?.trim() || host.phone?.trim() || host.email?.trim());
  }

  /**
   * 获取房东加入时长描述
   * 如：'房东2年' 或 '新房东'
   */
  static getJoinDuration(host: HostDTO | null): string {
    if (!host) return "房东";

    if (host.hostYears) {
      const years = parseInt(host.hostYears);
      return years > 1 ? `房东${years}年` : "房东1年";
    }

    if (host.hostSince) {
      const joinDate = new Date(host.hostSince);
      const now = new Date();
      const diffTime = Math.abs(now.getTime() - joinDate.getTime());
      const diffYears = Math.floor(diffTime / (1000 * 60 * 60 * 24 * 365));

      if (diffYears >= 1) {
        return `房东${diffYears}年`;
      }
    }

    return "新房东";
  }

  /**
   * 获取成就徽章列表
   * 根据房东数据计算应该显示的徽章
   */
  static getAchievementBadges(host: HostDTO | null): string[] {
    if (!host) return [];

    const badges: string[] = [];

    // 资深房东（房源数量>=3）
    if (host.homestayCount && host.homestayCount >= 3) {
      badges.push("资深房东");
    }

    // 接待达人（订单数量>=10）
    if (host.orderCount && host.orderCount >= 10) {
      badges.push("接待达人");
    }

    // 好评房东（评分>=4.0且评价数>=3）
    if (this.getDisplayRating(host) >= 4.0 && host.reviewCount && host.reviewCount >= 3) {
      badges.push("好评房东");
    }

    // 身份认证
    if (this.isVerified(host)) {
      badges.push("身份认证");
    }

    // 快速回复（有响应率信息）
    if (host.hostResponseRate && host.hostResponseTime) {
      badges.push("快速回复");
    }

    return badges;
  }

  /**
   * 格式化语言列表
   * 将语言代码转换为中文显示
   */
  static formatLanguages(languages: string[] | null | undefined): string[] {
    if (!languages || !Array.isArray(languages)) return [];

    return languages
      .map((lang) => LANGUAGE_MAP[lang.toUpperCase()] || lang)
      .filter((lang) => lang && lang.trim().length > 0);
  }

  /**
   * 获取完整的房东显示信息
   */
  static getDisplayInfo(host: HostDTO | null): HostDisplayInfo {
    return {
      displayName: this.getDisplayName(host),
      displayAvatar: this.getDisplayAvatar(host),
      displayRating: this.getDisplayRating(host),
      isVerified: this.isVerified(host),
      joinDuration: this.getJoinDuration(host),
      achievementBadges: this.getAchievementBadges(host),
    };
  }

  /**
   * 格式化统计数据
   */
  static getStatistics(host: HostDTO | null): HostStatistics {
    return {
      homestayCount: host?.homestayCount || 0,
      orderCount: host?.orderCount || 0,
      reviewCount: host?.reviewCount || 0,
      rating: this.getDisplayRating(host),
    };
  }

  /**
   * 格式化日期显示
   */
  static formatJoinDate(dateStr: string | Date | null | undefined): string {
    if (!dateStr) return "";

    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString("zh-CN", {
        year: "numeric",
        month: "long",
      });
    } catch {
      return dateStr.toString();
    }
  }

  /**
   * 获取房东专业度描述
   */
  static getProfessionalLevel(host: HostDTO | null): string {
    if (!host) return "新房东";

    const rating = this.getDisplayRating(host);
    const reviewCount = host.reviewCount || 0;
    const homestayCount = host.homestayCount || 0;

    if (rating >= 4.8 && reviewCount >= 50 && homestayCount >= 5) {
      return "超赞房东";
    } else if (rating >= 4.5 && reviewCount >= 20 && homestayCount >= 3) {
      return "优秀房东";
    } else if (rating >= 4.0 && reviewCount >= 5) {
      return "可信房东";
    } else {
      return "新房东";
    }
  }

  /**
   * 验证房东数据完整性
   */
  static validateHostData(host: any): host is HostDTO {
    return (
      host &&
      typeof host.id === "number" &&
      typeof host.username === "string" &&
      typeof host.email === "string"
    );
  }

  /**
   * 安全获取房东信息（带验证）
   */
  static safeGetHostInfo(host: any): HostDTO | null {
    if (this.validateHostData(host)) {
      return host;
    }
    console.warn("房东数据格式无效:", host);
    return null;
  }
}
