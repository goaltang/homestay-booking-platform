<template>
  <div class="about-container">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <h1>关于民宿预订平台</h1>
        <p class="hero-subtitle">连接美好住宿与每一位旅行者</p>
      </div>
    </section>

    <!-- Mission Section -->
    <section class="section mission-section">
      <div class="section-content">
        <div class="section-header">
          <h2>我们的使命</h2>
          <p class="section-desc">为旅行者提供独特、有温度的住宿体验</p>
        </div>
        <div class="mission-cards">
          <el-card class="mission-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon :size="32" class="mission-icon"><House /></el-icon>
                <span>精选房源</span>
              </div>
            </template>
            <p>严格审核每一套房源，确保住宿品质，让每一位房客都能找到心仪的第二个家。</p>
          </el-card>
          <el-card class="mission-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon :size="32" class="mission-icon"><User /></el-icon>
                <span>真实房东</span>
              </div>
            </template>
            <p>房东实名认证，房源真实描述，平台保障每一笔交易的安全与诚信。</p>
          </el-card>
          <el-card class="mission-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon :size="32" class="mission-icon"><ChatDotRound /></el-icon>
                <span>即时沟通</span>
              </div>
            </template>
            <p>内置实时聊天，房东与房客零距离沟通，预订前后全程贴心服务。</p>
          </el-card>
        </div>
      </div>
    </section>

    <!-- How It Works - For Guests -->
    <section class="section how-it-works">
      <div class="section-content">
        <div class="section-header">
          <h2>房客指南</h2>
          <p class="section-desc">轻松三步，开启您的特色住宿之旅</p>
        </div>
        <el-steps :active="activeStep" finish-status="success" align-center class="steps">
          <el-step
            title="搜索房源"
            description="浏览精选民宿，设置目的地、入住日期，筛选心仪的住宿"
          />
          <el-step title="预订支付" description="选择心仪房源，完成在线预订，支持支付宝安全支付" />
          <el-step title="入住体验" description="收到入住凭证，办理入住，享受温馨舒适的住宿体验" />
        </el-steps>
      </div>
    </section>

    <!-- How It Works - For Hosts -->
    <section class="section how-it-works host-section">
      <div class="section-content">
        <div class="section-header">
          <h2>房东入驻</h2>
          <p class="section-desc">把您的房源展示给千万旅行者</p>
        </div>
        <el-steps :active="activeHostStep" finish-status="success" align-center class="steps">
          <el-step title="提交申请" description="完成房东入驻流程，提交身份认证资料" />
          <el-step title="发布房源" description="填写房源信息，上传精美图片，设置合理价格" />
          <el-step title="接待旅客" description="接受预订请求，与房客沟通，提供优质住宿服务" />
        </el-steps>
      </div>
    </section>

    <!-- Features Section -->
    <section class="section features-section">
      <div class="section-content">
        <div class="section-header">
          <h2>平台特色</h2>
          <p class="section-desc">为什么选择我们的平台</p>
        </div>
        <div class="features-grid">
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><Location /></el-icon>
            <h3>位置优越</h3>
            <p>覆盖全国热门旅游城市与目的地</p>
          </div>
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><Wallet /></el-icon>
            <h3>价格透明</h3>
            <p>明码标价，无隐藏费用</p>
          </div>
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><CircleCheck /></el-icon>
            <h3>品质保障</h3>
            <p>房源人工审核，入住有保障</p>
          </div>
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><Lock /></el-icon>
            <h3>安全支付</h3>
            <p>支付宝担保交易，资金安全</p>
          </div>
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><Headset /></el-icon>
            <h3>客服支持</h3>
            <p>7×24 小时在线客服</p>
          </div>
          <div class="feature-item">
            <el-icon :size="48" class="feature-icon"><Bell /></el-icon>
            <h3>实时通知</h3>
            <p>订单状态实时推送，不错过任何消息</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats Section -->
    <section class="section stats-section">
      <div class="section-content">
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-number">10,000+</div>
            <div class="stat-label">精选房源</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">50,000+</div>
            <div class="stat-label">满意房客</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">5,000+</div>
            <div class="stat-label">优质房东</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">100+</div>
            <div class="stat-label">覆盖城市</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Contact Section -->
    <section class="section contact-section">
      <div class="section-content">
        <div class="section-header">
          <h2>联系我们</h2>
          <p class="section-desc">有任何问题或建议，我们随时恭候</p>
        </div>
        <div class="contact-cards">
          <el-card class="contact-card" shadow="hover">
            <el-icon :size="32" class="contact-icon"><Message /></el-icon>
            <h3>客服邮箱</h3>
            <p>support@homestay.com</p>
          </el-card>
          <el-card class="contact-card" shadow="hover">
            <el-icon :size="32" class="contact-icon"><Phone /></el-icon>
            <h3>客服热线</h3>
            <p>400-888-8888</p>
          </el-card>
          <el-card class="contact-card" shadow="hover">
            <el-icon :size="32" class="contact-icon"><OfficeBuilding /></el-icon>
            <h3>公司地址</h3>
            <p>北京市朝阳区某某路88号</p>
          </el-card>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import {
  House,
  User,
  ChatDotRound,
  Location,
  Wallet,
  CircleCheck,
  Lock,
  Headset,
  Bell,
  Message,
  Phone,
  OfficeBuilding,
} from "@element-plus/icons-vue";

const activeStep = ref(0);
const activeHostStep = ref(0);

onMounted(() => {
  // 依次展示步骤
  let step = 0;
  const stepInterval = setInterval(() => {
    if (step < 3) {
      activeStep.value = step;
      activeHostStep.value = step;
      step++;
    } else {
      clearInterval(stepInterval);
    }
  }, 400);
});
</script>

<style scoped>
.about-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

/* Hero Section */
.hero-section {
  background: linear-gradient(135deg, #ff385c 0%, #00a699 100%);
  color: white;
  padding: 80px 24px;
  text-align: center;
  border-radius: 0 0 24px 24px;
  margin-bottom: 48px;
}

.hero-content h1 {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
}

.hero-subtitle {
  font-size: 24px;
  opacity: 0.9;
}

/* Sections */
.section {
  padding: 64px 0;
}

.section-content {
  max-width: 1200px;
  margin: 0 auto;
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-header h2 {
  font-size: 36px;
  font-weight: 600;
  color: #222;
  margin-bottom: 12px;
}

.section-desc {
  font-size: 18px;
  color: #717171;
}

/* Mission Cards */
.mission-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.mission-card {
  text-align: center;
  border: none;
  background: #f9f9f9;
}

.card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
}

.mission-icon {
  color: #ff385c;
}

.mission-card p {
  color: #717171;
  line-height: 1.8;
}

/* Steps */
.steps {
  max-width: 800px;
  margin: 0 auto;
}

.how-it-works {
  background: #f9f9f9;
  border-radius: 24px;
  margin: 0 -24px;
  padding: 64px 24px;
}

.host-section {
  background: white;
  border: 2px solid #f0f0f0;
  margin-top: 24px;
}

/* Features */
.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
}

.feature-item {
  text-align: center;
  padding: 32px;
  background: #f9f9f9;
  border-radius: 16px;
  transition: transform 0.3s;
}

.feature-item:hover {
  transform: translateY(-4px);
}

.feature-icon {
  color: #ff385c;
  margin-bottom: 16px;
}

.feature-item h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #222;
}

.feature-item p {
  color: #717171;
}

/* Stats */
.stats-section {
  background: linear-gradient(135deg, #ff385c 0%, #00a699 100%);
  border-radius: 24px;
  margin: 24px 0;
  padding: 64px 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
  text-align: center;
  color: white;
}

.stat-number {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 16px;
  opacity: 0.9;
}

/* Contact */
.contact-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.contact-card {
  text-align: center;
  border: none;
  background: #f9f9f9;
}

.contact-icon {
  color: #ff385c;
  margin-bottom: 16px;
}

.contact-card h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #222;
}

.contact-card p {
  color: #717171;
}

/* Responsive */
@media (max-width: 768px) {
  .hero-content h1 {
    font-size: 32px;
  }

  .hero-subtitle {
    font-size: 18px;
  }

  .mission-cards,
  .features-grid,
  .contact-cards {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-number {
    font-size: 36px;
  }

  .section-header h2 {
    font-size: 28px;
  }
}
</style>
