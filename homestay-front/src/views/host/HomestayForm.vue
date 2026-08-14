<template>
    <div class="homestay-form-container">
        <el-card class="form-card">
            <template #header>
                <div class="card-header">
                    <h2>{{ isEdit ? '编辑房源' : '添加新房源' }}</h2>
                </div>
            </template>

            <!-- 添加进度指示器 -->
            <div class="form-progress">
                <el-steps :active="activeStep" finish-status="success" simple>
                    <el-step icon="HomeFilled">
                        <template #title><span @click="goToStep(1)" class="step-title">基本信息</span></template>
                    </el-step>
                    <el-step icon="Location">
                        <template #title><span @click="goToStep(2)" class="step-title">位置信息</span></template>
                    </el-step>
                    <el-step icon="Setting">
                        <template #title><span @click="goToStep(3)" class="step-title">设施与服务</span></template>
                    </el-step>
                    <el-step icon="Document">
                        <template #title><span @click="goToStep(4)" class="step-title">描述信息</span></template>
                    </el-step>
                    <el-step icon="Picture">
                        <template #title><span @click="goToStep(5)" class="step-title">房源图片</span></template>
                    </el-step>
                </el-steps>
            </div>

            <el-form ref="formRef" :model="homestayForm" :rules="rules" label-width="120px" class="homestay-form">
                <!-- 步骤内容区域 -->
                <div v-if="activeStep === 1">
                    <!-- 基本信息 -->
                    <div class="form-section">
                        <h3>基本信息 <span class="section-subtitle">— 创建吸引人的房源</span></h3>

                        <el-form-item label="房源标题" prop="title">
                            <el-input v-model="homestayForm.title" placeholder="请输入能吸引客人的标题，突出您房源的亮点" />
                            <div class="field-tips">
                                <div class="tip-title">创建好标题的技巧：</div>
                                <ul>
                                    <li>突出房源位置（如"临近西湖"、"地铁5分钟"）</li>
                                    <li>强调特色设施（如"带私人泳池"、"观景阳台"）</li>
                                    <li>理想长度为15-25个字符</li>
                                </ul>
                                <div class="examples">
                                    <div class="example-title">高质量标题示例：</div>
                                    <div class="example-item">💫 紧邻西湖景区 | 现代简约两居室 | 步行可达商圈</div>
                                </div>
                            </div>
                        </el-form-item>

                        <el-form-item label="房源类型" prop="type">
                            <PropertyTypeSelector v-model="homestayForm.type" />
                            <div class="field-tip">选择最能准确描述您整个房源的类型</div>
                        </el-form-item>

                        <el-form-item label="房源分组">
                            <el-select v-model="homestayForm.groupId" placeholder="选择分组（可选）" clearable style="width: 100%;">
                                <el-option v-for="g in groupOptions" :key="g.id" :label="g.name" :value="g.id">
                                    <span style="float: left;">
                                        <span v-if="g.color" :style="{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '2px', backgroundColor: g.color, marginRight: '8px' }"></span>
                                        {{ g.name }}
                                    </span>
                                    <span style="float: right; color: #8492a6; font-size: 13px;">{{ g.homestayCount }} 套</span>
                                </el-option>
                            </el-select>
                            <div class="field-tip">将房源归类到分组，方便管理（可稍后在房源管理中调整）</div>
                        </el-form-item>

                        <el-form-item label="每晚价格" prop="price">
                            <div class="price-input-container">
                                <span class="price-symbol">¥</span>
                                <el-input-number v-model="homestayForm.price" :min="0" :precision="2" :step="10"
                                    controls-position="right" style="width: 100%" />
                            </div>

                            <div class="price-assistant">
                                <div class="market-insight">
                                    <span class="insight-title">💡 价格参考：</span>
                                    <span class="insight-content">您所在区域类似房源的价格范围为 ¥198-¥368/晚</span>
                                </div>

                                <div class="pricing-tips">
                                    <div class="tip-title">定价建议：</div>
                                    <ul>
                                        <li>新房源可设置略低于市场的价格，获得首批评价</li>
                                        <li>周末和节假日可考虑设置更高价格</li>
                                        <li>长期入住可提供折扣以增加预订率</li>
                                    </ul>
                                </div>
                            </div>
                        </el-form-item>

                        <el-form-item label="预订确认方式" prop="autoConfirm">
                            <div class="booking-confirm-container">
                                <el-radio-group v-model="homestayForm.autoConfirm" @change="handleConfirmModeChange">
                                    <el-radio-button :value="true">
                                        <el-icon style="margin-right: 5px;"><Lightning /></el-icon>
                                        自动确认 <el-tag size="small" type="danger" effect="dark" style="margin-left: 4px; border-radius: 10px;">推荐</el-tag>
                                    </el-radio-button>
                                    <el-radio-button :value="false">
                                        <el-icon style="margin-right: 5px;"><User /></el-icon>
                                        房东确认
                                    </el-radio-button>
                                </el-radio-group>
                                
                                <div class="confirm-mode-description">
                                    <div v-if="homestayForm.autoConfirm" class="auto-confirm-info">
                                        <el-icon class="info-icon"><InfoFilled /></el-icon>
                                        <div class="info-content">
                                            <p><strong>自动确认模式</strong>：客人预订后订单立即确认，可直接支付</p>
                                            <ul>
                                                <li>✅ 提高预订转化率，用户体验更好</li>
                                                <li>✅ 减少客人等待时间，提升竞争力</li>
                                                <li>⚠️ 适合价格在500元/晚以下的房源</li>
                                            </ul>
                                        </div>
                                    </div>
                                    <div v-else class="manual-confirm-info">
                                        <el-icon class="info-icon"><InfoFilled /></el-icon>
                                        <div class="info-content">
                                            <p><strong>房东确认模式</strong>：客人预订后需要您确认才能支付</p>
                                            <ul>
                                                <li>✅ 您可以筛选客人，拒绝不合适的预订</li>
                                                <li>✅ 适合高价房源或有特殊要求的房源</li>
                                                <li>⚠️ 需要及时处理预订请求，避免客人流失</li>
                                            </ul>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </el-form-item>

                        <div class="guests-nights-container">
                            <el-form-item label="最大入住人数" prop="maxGuests" class="half-width">
                                <div class="guest-selector">
                                    <el-input-number v-model="homestayForm.maxGuests" :min="1" :max="20"
                                        controls-position="right" />
                                    <div class="selector-description">包括成人和儿童总数</div>
                                </div>
                                <div class="field-tip">设置合理的人数上限有助于保护您的房屋</div>
                            </el-form-item>

                            <el-form-item label="最少入住晚数" prop="minNights" class="half-width">
                                <div class="nights-selector">
                                    <el-input-number v-model="homestayForm.minNights" :min="1" :max="30"
                                        controls-position="right" />
                                    <div class="selector-description">建议1-3晚可提高预订率</div>
                                </div>
                                <div class="field-tip">过长的最少预订晚数可能会降低订单量</div>
                            </el-form-item>
                        </div>
                    </div>
                </div>

                <div v-if="activeStep === 2">
                    <!-- 位置信息 -->
                    <div class="form-section">
                        <h3>位置信息 <span class="section-subtitle">— 帮助客人找到您的房源</span></h3>

                        <el-alert v-if="locationDataError" :title="locationDataError" type="error" :closable="false"
                            style="margin-bottom: 15px;" />

                        <div class="location-actions" v-if="provinces.length === 0">
                            <el-button type="primary" size="small" @click="reloadLocationData"
                                :loading="loadingLocationData" style="margin-bottom: 15px;">
                                重新加载位置数据
                            </el-button>
                        </div>

                        <!-- 使用级联选择器替换省市区下拉框 -->
                        <el-form-item label="所在地区" prop="provinceCode"> <!-- 或者验证 selectedAreaCodes -->
                            <el-cascader v-model="selectedAreaCodes" :options="areaData" placeholder="请选择省/市/区"
                                clearable style="width: 100%;" @change="handleAreaChange" />
                        </el-form-item>

                        <el-form-item label="详细地址" prop="addressDetail"> <!-- 修改 prop -->
                            <el-input v-model="homestayForm.addressDetail" placeholder="请输入街道名称、门牌号等详细地址"
                                type="textarea" :rows="2" />
                            <div class="field-tip">详细地址仅在客人预订后显示，以保护您的隐私</div>
                        </el-form-item>
                    </div>
                </div>

                <div v-if="activeStep === 3">
                    <!-- 设施与服务 -->
                    <div class="form-section">
                        <h3>设施与服务 <span class="section-subtitle">— 突出您的房源特色</span></h3>
                        <div class="amenities-tip">
                            选择您房源提供的所有设施和服务，这将帮助客人了解您房源的舒适度和便利性。
                            提供更多设施可能会提高您的房源吸引力！
                        </div>
                        <el-form-item label="设施服务" prop="amenities">
                            <AmenitySelector v-model="homestayForm.amenities" />
                        </el-form-item>
                    </div>
                </div>

                <div v-if="activeStep === 4">
                    <!-- 描述信息 -->
                    <div class="form-section description-section">
                        <h3>房源描述 <span class="section-subtitle">— 展示您房源的独特魅力</span></h3>

                        <div class="description-guide">
                            <p>详细、真实的描述能让客人了解您的房源特色，提高预订率和客人满意度。</p>
                        </div>

                        <el-form-item label="房源亮点" prop="highlights">
                            <el-input v-model="homestayForm.highlights" type="textarea" :rows="3"
                                placeholder="列出3-5个房源最吸引人的特点，如'270度观景落地窗'、'步行可达地铁站'等" />
                        </el-form-item>

                        <el-form-item label="详细描述" prop="description">
                            <div class="description-tips">
                                <span class="tips-title">描述建议包含：</span>
                                <span>房间布局、床型详情、设施特色、周边环境、交通便利性、适合人群等</span>
                            </div>
                            <el-input v-model="homestayForm.description" type="textarea" :rows="5"
                                placeholder="详细描述您的房源，让客人对入住体验有清晰的预期" />
                        </el-form-item>

                        <el-form-item label="周边环境" prop="surroundings">
                            <el-input v-model="homestayForm.surroundings" type="textarea" :rows="3"
                                placeholder="描述房源周边环境，如商场、餐厅、交通、景点等信息" />
                        </el-form-item>
                    </div>
                    
                    <!-- 须知事项及规则 -->
                    <div class="form-section description-section" style="margin-top: 24px;">
                        <h3>政策与规则 <span class="section-subtitle">— 让客人了解入住要求</span></h3>

                        <el-form-item label="入住时间" prop="checkInTime">
                            <el-time-select v-model="homestayForm.checkInTime" start="08:00" step="01:00" end="24:00" placeholder="选择入住开始时间" />
                        </el-form-item>

                        <el-form-item label="退房时间" prop="checkOutTime">
                            <el-time-select v-model="homestayForm.checkOutTime" start="06:00" step="01:00" end="18:00" placeholder="选择最晚退房时间" />
                        </el-form-item>

                        <el-form-item label="退订政策" prop="cancelPolicyType">
                            <el-radio-group v-model="homestayForm.cancelPolicyType">
                                <el-radio :value="1">宽松 (入住前24小时取消全额退款，24小时内扣首晚)</el-radio>
                                <el-radio :value="2">普通 (入住前48小时全额，24-48小时退50%，24小时内扣首晚)</el-radio>
                                <el-radio :value="3">严格 (入住前72小时全额，72小时内退50%)</el-radio>
                            </el-radio-group>
                        </el-form-item>

                        <el-form-item label="房屋守则" prop="houseRules">
                            <el-input v-model="homestayForm.houseRules" type="textarea" :rows="4"
                                placeholder="输入您对客人的额外要求，例如：请勿吸烟、保持安静、禁止派对等" />
                        </el-form-item>
                    </div>
                </div>

                <div v-if="activeStep === 5">
                    <!-- 图片上传 -->
                    <div class="form-section">
                        <h3>房源图片 <span class="section-subtitle">— 展示房源的视觉魅力</span></h3>

                        <!-- 图片上传问题调试工具 -->
                        <el-alert v-if="uploadError" type="error" :title="uploadErrorTitle"
                            :description="uploadErrorMessage" show-icon closable @close="clearUploadError">
                            <template #default>
                                <div class="upload-error-actions">
                                    <el-button size="small" type="primary" @click="retryLastUpload">
                                        重试上传
                                    </el-button>
                                    <el-button size="small" @click="toggleDebugInfo">
                                        {{ showDebugInfo ? '隐藏详情' : '显示详情' }}
                                    </el-button>
                                </div>
                                <div v-if="showDebugInfo" class="debug-info">
                                    <div class="debug-section">
                                        <h4>错误详情</h4>
                                        <pre>{{ JSON.stringify(uploadErrorDetails, null, 2) }}</pre>
                                    </div>
                                </div>
                            </template>
                        </el-alert>

                        <div class="image-upload-guide">
                            <p>高质量的图片能极大提升您房源的吸引力。建议上传明亮、整洁的房间照片，包括卧室、客厅、卫生间等主要空间。</p>
                        </div>

                        <el-form-item label="封面图片" prop="coverImage">
                            <div class="upload-wrapper">
                                <div class="preview-container" v-if="homestayForm.coverImage">
                                    <img :src="homestayForm.coverImage" class="preview-image" />
                                    <div class="image-actions">
                                        <el-button type="danger" icon="Delete" circle size="small"
                                            @click="removeCoverImage" />
                                    </div>
                                </div>
                                <div v-else class="cover-upload-guide">
                                    <el-upload class="upload-cover" :http-request="handleCustomUpload('cover')"
                                        :show-file-list="false" :disabled="loading">
                                        <div class="upload-icon"><el-icon>
                                                <Picture />
                                            </el-icon></div>
                                        <div class="upload-text">封面图片是您房源的第一印象</div>
                                        <el-button type="primary" :loading="uploadingCover">
                                            选择封面图片
                                        </el-button>
                                    </el-upload>
                                </div>
                            </div>
                            <div class="upload-tip">推荐选择能展示房源整体特色的照片，建议尺寸800x600像素，不超过2MB</div>
                        </el-form-item>

                        <el-form-item label="房源图片集" prop="images">
                            <div class="gallery-container">
                                <div class="gallery-images"
                                    v-if="homestayForm.images && homestayForm.images.length > 0">
                                    <div v-for="(image, index) in homestayForm.images" :key="index"
                                        class="gallery-item">
                                        <img :src="image" class="gallery-image" />
                                        <div class="image-actions">
                                            <el-button type="danger" icon="Delete" circle size="small"
                                                @click="removeGalleryImage(index)" />
                                        </div>
                                    </div>
                                </div>
                                <div class="gallery-upload"
                                    v-if="!homestayForm.images || homestayForm.images.length < 9">
                                    <div class="upload-guide"
                                        v-if="!homestayForm.images || homestayForm.images.length === 0">
                                        <p>请添加至少5张不同角度的房源照片，包括：</p>
                                        <ul>
                                            <li>每个卧室的照片</li>
                                            <li>客厅和厨房区域</li>
                                            <li>每个卫生间</li>
                                            <li>特色设施或空间（如阳台、花园等）</li>
                                        </ul>
                                    </div>
                                    <el-upload class="upload-gallery" :http-request="handleCustomUpload('gallery')"
                                        :show-file-list="false" :disabled="loading">
                                        <el-button type="primary" :loading="uploadingGallery">
                                            添加房源图片
                                        </el-button>
                                    </el-upload>
                                </div>
                            </div>
                            <div class="upload-tip">最多上传9张图片，每张不超过2MB。上传清晰、明亮的照片能吸引更多客人</div>
                        </el-form-item>
                    </div>
                </div>

                <!-- 步骤切换按钮 -->
                <div class="form-step-actions">
                    <el-button @click="prevStep" :disabled="activeStep === 1 || loading">上一步</el-button>
                    <el-button v-if="activeStep < 5" type="primary" @click="nextStep">下一步</el-button>
                    <el-button v-else type="success" @click="handleSubmit" :loading="loading">
                        {{ getSubmitButtonText() }}
                    </el-button>
                    <el-button type="info" @click="saveDraft" :loading="savingDraft">保存草稿</el-button>

                    <!-- 认证检查按钮 -->
                    <el-button type="warning" @click="checkAuthentication" :loading="checkingAuth"
                        style="margin-left: 10px;">
                        检查认证状态
                    </el-button>

                    <div class="save-status" v-if="lastSaved">
                        <el-tooltip :content="'上次保存时间: ' + lastSaved?.toLocaleString()" placement="top">
                            <span><i class="el-icon-time"></i> {{ formatLastSavedText(lastSaved) }}</span>
                        </el-tooltip>
                    </div>
                </div>

                <!-- 完成度指示器 -->
                <div class="form-completion">
                    <div class="completion-text">表单完成度: <strong>{{ calculateFormCompletion(homestayForm) }}%</strong></div>
                    <el-progress :percentage="calculateFormCompletion(homestayForm)"
                        :status="calculateFormCompletion(homestayForm) === 100 ? 'success' : ''"></el-progress>
                </div>
            </el-form>
        </el-card>

        <!-- 侧边预览面板 -->
        <div class="preview-panel" v-if="showPreview">
            <div class="preview-header">
                <h3>房源预览</h3>
                <el-button type="text" @click="showPreview = false">关闭</el-button>
            </div>
            <div class="preview-content">
                <div class="preview-image">
                    <img :src="homestayForm.coverImage || '/img/no-image.png'" alt="房源封面" />
                </div>
                <div class="preview-info">
                    <h4>{{ homestayForm.title || '房源标题' }}</h4>
                    <div class="preview-type">{{ getHomestayTypeText(homestayForm.type, homestayTypes) }}</div>
                    <div class="preview-price">¥{{ homestayForm.price || '0' }}/晚</div>
                    <div class="preview-location">
                        {{ getLocationText() }}
                    </div>
                    <div class="preview-highlights">
                        {{ homestayForm.highlights || '房源亮点' }}
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import { regionData, codeToText } from 'element-china-area-data' // 导入省市区数据 和 codeToText (小写c)
import {
    Picture,
    Lightning, User, InfoFilled
} from '@element-plus/icons-vue'
import {
    getHomestayById,
    getHomestayTypes, // 这个API可能不再需要，或者需要调整用途
    updateHomestay as updateHomestayApi,
    createHomestay as createHomestayApi,
    saveHomestayDraft,
    getHomestayGroups,
    getProvinces
} from '@/api/homestay'
import {
    getAmenitiesByCategoryApi
} from '@/api/amenities'
import AmenitySelector from '@/components/AmenitySelector.vue'
import { useHomestayImageUpload } from '@/composables/useHomestayImageUpload'
import { getHomestayTypeText, processAmenities, formatLastSavedText, calculateFormCompletion } from '@/utils/homestayForm'
import type { Homestay } from '@/types/homestay'
import PropertyTypeSelector from '@/components/PropertyTypeSelector.vue'
import { checkAuthentication as checkAuthAPI, ensureUserLoggedIn } from "@/utils/auth"

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!route.params.id)
const homestayId = computed(() => Number(route.params.id))

// 步骤和预览相关
const activeStep = ref(1);
const showPreview = ref(true);
const savingDraft = ref(false);

// --- 步骤跳转控制 --- 
const goToStep = (stepIndex: number) => {
    if (stepIndex < activeStep.value) {
        // 允许回退到已完成的步骤
        activeStep.value = stepIndex;
    } else if (stepIndex === activeStep.value) {
        // 无操作
        return;
    } else {
        // 禁止通过点击标题跳过未验证的步骤
        ElMessage.warning('请点击"下一步"按顺序完成表单');
    }
};

// 上传文件相关
const fileList = ref<UploadUserFile[]>([])

// 添加位置数据加载状态
const loadingLocationData = ref(false)
const locationDataError = ref('')

// 添加自动保存提示和状态
const lastSaved = ref<Date | null>(null)
const autoSaveInterval = ref<number | null>(null)

// 添加上传错误处理相关状态

// 级联选择器状态
const selectedAreaCodes = ref<string[]>([])
const areaData = regionData // 将导入的数据赋值给模板中使用的变量

// 分组选项
const groupOptions = ref<any[]>([])
const groupsLoading = ref(false)

const fetchGroupOptions = async () => {
    try {
        groupsLoading.value = true
        const groups = await getHomestayGroups()
        groupOptions.value = groups.filter((g: any) => g.enabled)
    } catch (error) {
        console.error('获取分组列表失败:', error)
        groupOptions.value = []
    } finally {
        groupsLoading.value = false
    }
}

// 扩展表单数据 - 修改地址相关字段
const homestayForm = reactive<Partial<Homestay> & { // 使用 Partial 允许部分字段缺失
    highlights?: string;
    surroundings?: string;
    provinceCode?: string; // 新增
    cityCode?: string;     // 新增
    districtCode?: string; // 新增
    addressDetail?: string;// 新增
    groupId?: number;      // 新增：房源分组
}>({ // 移除旧字段，添加新字段并设置初始值
    id: undefined,
    title: '',
    type: '',
    price: '',
    status: 'DRAFT', // 新房源默认为草稿状态
    maxGuests: 1,
    minNights: 1,
    provinceCode: '',
    cityCode: '',
    districtCode: '',
    addressDetail: '',
    amenities: [],
    description: '',
    coverImage: '',
    images: [],
    featured: false,
    autoConfirm: true, // 默认为自动确认机制（推荐）
    highlights: '',
    surroundings: '',
    checkInTime: '14:00',
    checkOutTime: '12:00',
    cancelPolicyType: 2,  // 默认普通政策
    houseRules: '',
    groupId: undefined,
})

// 图片上传逻辑（封面 + 图库）
const {
    uploadingCover,
    uploadingGallery,
    uploadError,
    uploadErrorTitle,
    uploadErrorMessage,
    uploadErrorDetails,
    showDebugInfo,
    handleCustomUpload,
    retryLastUpload,
    clearUploadError,
    toggleDebugInfo,
    removeGalleryImage,
    removeCoverImage,
} = useHomestayImageUpload(homestayForm, homestayId)

// 更新表单验证规则
const rules = {
    title: [{ required: true, message: '请输入房源标题', trigger: 'blur' }],
    type: [{ required: true, message: '请选择房源类型', trigger: 'change' }],
    price: [{ required: true, message: '请输入房源价格', trigger: 'blur' }],
    maxGuests: [{ required: true, message: '请设置最大入住人数', trigger: 'blur' }],
    minNights: [{ required: true, message: '请设置最少入住晚数', trigger: 'blur' }],
    // 修改地址校验规则
    provinceCode: [{ required: true, message: '请选择所在地区', trigger: 'change' }], // 间接校验级联选择器
    addressDetail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
    description: [{ required: true, message: '请输入房源描述', trigger: 'blur' }],
    coverImage: [{ required: true, message: '请上传房源封面图片', trigger: 'change' }],
    highlights: [{ required: false, message: '请输入房源亮点', trigger: 'blur' }],
    surroundings: [{ required: false, message: '请描述周边环境', trigger: 'blur' }],
}

// 数据列表
const provinces = ref<any[]>([])
const cities = ref<any[]>([])
const districts = ref<any[]>([])
const homestayTypes = ref<any[]>([])
const amenities = ref<any[]>([])

// 步骤导航方法
const nextStep = async () => {
    if (activeStep.value < 5) {
        try {
            let validateSuccess = true;

            if (activeStep.value === 1) {
                // 验证基本信息字段
                try {
                    // 逐个验证字段
                    await formRef.value?.validateField('title');
                    await formRef.value?.validateField('type');
                    await formRef.value?.validateField('price');
                    await formRef.value?.validateField('maxGuests');
                    await formRef.value?.validateField('minNights');
                } catch (error) {
                    validateSuccess = false;
                }
            } else if (activeStep.value === 2) {
                // 验证位置信息（cityCode/districtCode 由级联选择器联动产生，无需单独校验）
                try {
                    await formRef.value?.validateField('provinceCode');
                    await formRef.value?.validateField('addressDetail');
                } catch (error) {
                    validateSuccess = false;
                }
            } else if (activeStep.value === 4) {
                // 验证描述信息
                try {
                    await formRef.value?.validateField('description');
                } catch (error) {
                    validateSuccess = false;
                }
            }

            if (validateSuccess) {
                activeStep.value++;
            } else {
                ElMessage.warning('请完成必填字段后再继续');
            }
        } catch (error) {
            console.error('表单验证错误:', error);
            ElMessage.warning('请完成必填字段后再继续');
            return;
        }
    }
};

const prevStep = () => {
    if (activeStep.value > 1) {
        activeStep.value--;
    }
};

/**
 * 预处理表单数据
 */
const preprocessFormData = () => {
    const processedData = JSON.parse(JSON.stringify(homestayForm)) as any;

    // 添加用户名(所有者)
    try {
        const userInfoStr = (localStorage.getItem('homestay_user') || localStorage.getItem('userInfo'))
        if (userInfoStr) {
            const userInfo = JSON.parse(userInfoStr)
            if (userInfo && userInfo.username) {
                processedData.ownerUsername = userInfo.username
            } else {
                processedData.ownerUsername = ''
            }
        } else {
            processedData.ownerUsername = ''
        }
    } catch (e) {
        console.error('获取用户信息失败', e);
        processedData.ownerUsername = ''
    }

    // 处理价格
    if (processedData.price && typeof processedData.price === 'string') {
        processedData.price = String(parseFloat(processedData.price))
    }

    // 处理最大/最小入住
    if (processedData.maxGuests) {
        processedData.maxGuests = Number(processedData.maxGuests)
    }
    if (processedData.minNights) {
        processedData.minNights = Number(processedData.minNights)
    }

    // 处理设施数据
    if (processedData.amenities) {
        if (Array.isArray(processedData.amenities)) {
            const amenityValues = processedData.amenities.map((amenity: any) => {
                if (typeof amenity === 'string') return amenity
                if (typeof amenity === 'object' && amenity !== null) return amenity.value || ''
                return ''
            }).filter(Boolean)
            processedData.amenities = amenityValues
        } else {
            processedData.amenities = []
        }
    } else {
        processedData.amenities = []
    }

    // 确保必要字段有默认值，用于草稿保存
    if (!processedData.title) processedData.title = '';
    if (!processedData.type) processedData.type = '';
    if (!processedData.description) processedData.description = '';
    if (!processedData.status) processedData.status = 'DRAFT';

    return processedData
}

/**
 * 获取提交按钮文本
 */
const getSubmitButtonText = () => {
    if (isEdit.value) {
        // 编辑模式：根据当前状态决定按钮文本
        if (homestayForm.status === 'DRAFT') {
            return '提交审核'
        } else {
            return '保存修改'
        }
    } else {
        // 新建模式：直接提交审核
        return '提交审核'
    }
}

/**
 * 保存草稿
 */
const saveDraft = async () => {
    try {
        savingDraft.value = true;

        // 获取处理后的表单数据，但不需要完整验证
        const processedData = preprocessFormData();

        // 使用专门的草稿保存API
        const result = await saveHomestayDraft(processedData);

        if (result.data) {
            ElMessage.success('房源草稿保存成功');
            lastSaved.value = new Date();

            // 如果是新创建的草稿，更新表单状态并导航到编辑页面
            if (!isEdit.value && result.data.id) {
                // 更新表单数据中的ID和状态
                homestayForm.id = result.data.id;
                homestayForm.status = 'DRAFT';

                // 导航到编辑页面
                router.replace(`/host/homestay/edit/${result.data.id}`);
            }
        } else {
            ElMessage.error(result.message || '保存草稿失败，请稍后重试');
        }
    } catch (error) {
        console.error('保存草稿出错:', error);

        // 更详细的错误处理
        let errorMessage = '保存草稿失败';
        if (error instanceof Error) {
            errorMessage += ': ' + error.message;
        } else if (typeof error === 'object' && error !== null && 'response' in error) {
            const axiosError = error as any;
            if (axiosError.response?.data?.message) {
                errorMessage += ': ' + axiosError.response.data.message;
            } else if (axiosError.response?.status) {
                errorMessage += ': HTTP ' + axiosError.response.status;
            }
        }

    } finally {
        savingDraft.value = false;
    }
}

/**
 * 提交表单
 */
const handleSubmit = async () => {
    try {
        const form = formRef.value;
        if (!form) {
            ElMessage.error('表单不存在');
            return;
        }

        // 验证表单
        await form.validate();

        // 深拷贝，避免直接修改formData
        const submitData = JSON.parse(JSON.stringify(homestayForm));

        // 处理设施数据
        const amenitiesData = processAmenities(submitData.amenities || []);
        console.log('处理后的设施数据:', amenitiesData);

        // 先移除设施数据，减少保存时的错误风险
        const dataWithoutAmenities = { ...submitData };
        delete dataWithoutAmenities.amenities;

        console.log('第一步：准备提交不含设施的数据:', dataWithoutAmenities);

        loading.value = true;

        // 根据模式和当前状态确定提交方法
        let targetStatus = dataWithoutAmenities.status;

        // 如果是草稿状态的房源，提交时改为待审核状态
        if (homestayForm.status === 'DRAFT') {
            targetStatus = 'PENDING';
        } else if (!targetStatus) {
            // 新建房源默认状态
            targetStatus = 'PENDING';
        }

        const formToSubmit = {
            ...dataWithoutAmenities,
            // 确保其他必要的字段存在
            status: targetStatus,
            maxGuests: Number(dataWithoutAmenities.maxGuests) || 1,
            minNights: Number(dataWithoutAmenities.minNights) || 1
        };

        try {
            let response;

            if (isEdit.value) {
                // 编辑模式 - 先提交不含设施的数据
                const homestayId = Number(route.params.id);
                console.log('编辑房源(不含设施)', homestayId, formToSubmit);

                // 确保当前有有效用户名
                let username = '';
                try {
                    const userInfo = JSON.parse((localStorage.getItem('homestay_user') || localStorage.getItem('userInfo')) || '{}');
                    username = userInfo.username || '';
                } catch (e) {
                    console.warn('无法获取用户信息', e);
                }

                // 确保设置了所有者用户名，这对于权限验证很重要
                formToSubmit.ownerUsername = username;

                // 发送请求前再次检查权限
                const hasPermission = await checkEditPermission(homestayId);
                if (!hasPermission) {
                    ElMessage.error('没有权限执行此操作');
                    loading.value = false;
                    return;
                }

                // 发送请求
                response = await updateHomestayApi(homestayId, formToSubmit);
                console.log('服务器响应(编辑基本信息):', response.data);

                // 第二步：设施单独处理（如果有设施）
                if (amenitiesData && amenitiesData.length > 0) {
                    console.log('第二步：开始更新设施数据');

                    // 导入设施相关API
                    const { removeAllAmenitiesFromHomestayApi, addAmenityToHomestayApi } = await import('@/api/amenities');

                    // 先移除所有设施
                    await removeAllAmenitiesFromHomestayApi(homestayId);
                    console.log('已移除所有旧设施');

                    // 逐个添加新设施
                    for (const amenity of amenitiesData) {
                        try {
                            if (amenity && typeof amenity === 'object' && 'value' in amenity) {
                                await addAmenityToHomestayApi(homestayId, String(amenity.value));
                                console.log(`已添加设施: ${amenity.value}`);
                            } else {
                                console.warn('跳过无效的设施数据:', amenity);
                            }
                        } catch (err) {
                            console.error(`添加设施失败:`, err);
                        }
                    }

                    console.log('所有设施更新完成');
                }

                // 根据状态显示不同的成功消息
                if (homestayForm.status === 'DRAFT') {
                    ElMessage.success('房源已提交审核！');
                } else {
                    ElMessage.success('房源更新成功！');
                }
            } else {
                // 新建模式 - 先提交不含设施的数据
                console.log('创建新房源(不含设施)', formToSubmit);

                response = await createHomestayApi(formToSubmit);
                console.log('服务器响应(创建基本信息):', response.data);

                // 如果创建成功且有设施数据，逐个添加设施
                if (response.data && response.data.id && amenitiesData && amenitiesData.length > 0) {
                    const newHomestayId = response.data.id;
                    console.log(`房源已创建，ID: ${newHomestayId}，开始添加设施`);

                    // 导入设施相关API
                    const { addAmenityToHomestayApi } = await import('@/api/amenities');

                    // 逐个添加设施
                    for (const amenity of amenitiesData) {
                        try {
                            if (amenity && typeof amenity === 'object' && 'value' in amenity) {
                                await addAmenityToHomestayApi(newHomestayId, String(amenity.value));
                                console.log(`已添加设施: ${amenity.value}`);
                            } else {
                                console.warn('跳过无效的设施数据:', amenity);
                            }
                        } catch (err) {
                            console.error(`添加设施失败:`, err);
                        }
                    }

                    console.log('所有设施添加完成');
                }

                ElMessage.success('房源已提交审核！');
            }

            console.log('服务器响应:', response);

            if (response.data) {
                // 成功提交后，清除草稿
                localStorage.removeItem('homestayDraft');

                // 显示成功信息
                const successMessage = homestayForm.status === 'DRAFT'
                    ? '房源已提交审核！审核结果将在1-2个工作日内通知您。'
                    : `房源${isEdit.value ? '更新' : '创建'}成功！您可以在房源管理页面查看并管理您的房源。`;

                ElMessageBox.alert(
                    successMessage,
                    '操作成功',
                    {
                        confirmButtonText: '返回房源管理',
                        type: 'success',
                        callback: () => {
                            router.push('/host/homestay');
                        }
                    }
                );
            } else {
                throw new Error('操作失败，服务器未返回数据');
            }
        } catch (error: any) {
            console.error('提交表单失败:', error);

        } finally {
            loading.value = false;
        }
    } catch (error) {
        console.error('表单验证失败:', error);
    }
}

// 获取下拉选项
const fetchOptions = async () => {
    try {
        const typeRes = await getHomestayTypes()
        homestayTypes.value = typeRes || []

        const amenitiesRes = await getAmenitiesByCategoryApi()
        amenities.value = amenitiesRes?.data?.data || []

        await fetchGroupOptions()
    } catch (error) {
        console.error('加载选项数据失败', error)
        ElMessage.warning('加载基础数据失败，部分功能可能受限');
    }
}




// 在组件挂载时设置自动保存定时器
onMounted(async () => {
    console.log('HomestayForm组件加载，正在初始化...');

    // 打印当前角色信息便于调试
    const userInfo = (localStorage.getItem('homestay_user') || localStorage.getItem('userInfo'));
    if (userInfo) {
        try {
            const userObj = JSON.parse(userInfo);
            console.log('当前登录用户信息:', {
                id: userObj.id,
                username: userObj.username,
                role: userObj.role
            });
        } catch (e) {
            console.error('解析用户信息失败', e);
        }
    } else {
        console.warn('本地存储中没有用户信息');
    }

    // 添加权限检查，确保用户是HOST或LANDLORD角色
    // 注意：后端角色可能包含ROLE_前缀
    const hasPermission = await ensureUserLoggedIn(['HOST', 'LANDLORD', 'ROLE_HOST', 'ROLE_LANDLORD']);
    if (!hasPermission) {
        console.error('用户没有房东权限，无法编辑房源');
        ElMessage.error('您需要房东权限才能编辑房源信息');
        // 3秒后重定向到主页
        setTimeout(() => {
            router.push('/');
        }, 3000);
        return; // 权限检查函数会自动处理重定向
    }

    console.log('角色权限检查通过，继续初始化');

    // 编辑模式：还需检查房源所有权
    if (isEdit.value) {
        console.log(`编辑模式，房源ID: ${homestayId.value}`);

        // 导入所有权检查函数
        const { checkHomestayOwnership } = await import('@/api/homestay');

        // 检查用户是否是该房源的拥有者
        try {
            const isOwner = await checkHomestayOwnership(homestayId.value);
            if (!isOwner) {
                ElMessage.error('您不是该房源的所有者，无法编辑');
                // 3秒后返回房源列表
                setTimeout(() => {
                    router.push('/host/homestays');
                }, 3000);
                return;
            }
            console.log('所有权检查通过，用户可以编辑此房源');
        } catch (error) {
            console.error('检查房源所有权失败:', error);
            return;
        }
    }

    // 获取下拉选项数据
    try {
        await fetchOptions();
        console.log('选项数据加载成功');
    } catch (error) {
        console.error('获取选项数据失败:', error);
        ElMessage.warning('加载基础数据失败，部分功能可能受限');
    }

    // 编辑模式：加载现有房源数据
    if (isEdit.value) {
        try {
            console.log(`开始加载房源ID:${homestayId.value}的详细信息`);
            await initEditForm();
            console.log('房源数据加载成功');
        } catch (error) {
            console.error('加载房源数据失败:', error);
        }
    } else {
        // 新增模式：加载草稿或设置默认值
        console.log('新增模式，设置默认值');
        loadDraft();
    }

    // 设置自动保存
    autoSaveInterval.value = window.setInterval(autoSaveDraft, 3 * 60 * 1000);
    console.log('初始化完成，已设置自动保存');
});

// 在组件卸载时清除定时器
onUnmounted(() => {
    if (autoSaveInterval.value) {
        clearInterval(autoSaveInterval.value)
    }
})

// 自动保存草稿
const autoSaveDraft = () => {
    if (calculateFormCompletion(homestayForm) > 0) {
        // 将当前表单数据保存为草稿
        const draftData = {
            ...homestayForm,
            status: 'DRAFT', // 设置状态为草稿
            _lastSaved: new Date().toISOString()
        };

        // 保存到本地存储
        localStorage.setItem('homestayDraft', JSON.stringify(draftData));
        lastSaved.value = new Date();
        console.log('自动保存草稿完成:', new Date().toLocaleTimeString());
    }
}

// 添加重新加载位置数据函数
const reloadLocationData = async () => {
    try {
        // 显示加载状态
        loadingLocationData.value = true;
        locationDataError.value = '';

        // 清除现有数据
        provinces.value = [];
        cities.value = [];
        districts.value = [];

        // 重新加载省份数据
        const provinceRes = await getProvinces();
        console.log('省份数据响应:', provinceRes);

        if (provinceRes?.data?.data && Array.isArray(provinceRes.data.data)) {
            provinces.value = provinceRes.data.data;
            ElMessage.success(`成功加载 ${provinces.value.length} 个省份`);
            locationDataError.value = '';
        } else {
            locationDataError.value = '省份数据格式不正确，请联系管理员';
            ElMessage.error('省份数据格式不正确');
            console.error('省份数据格式不正确:', provinceRes);
        }
    } catch (error) {
        console.error('重新加载位置数据失败:', error);
        locationDataError.value = '位置数据加载失败，请稍后再试';
    } finally {
        loadingLocationData.value = false;
    }
};

// 添加认证检查相关状态
const checkingAuth = ref(false);

// 检查认证状态的辅助函数
const checkAuthentication = async () => {
    checkingAuth.value = true;
    try {
        // 调用API中的认证检查函数
        await checkAuthAPI();

        // 显示成功消息
        ElMessage.success('认证检查完成，请查看控制台日志获取详细信息');
    } catch (error: any) {
        console.error('认证检查失败:', error);
    } finally {
        checkingAuth.value = false;
    }
};

// 初始化房源编辑表单
const initEditForm = async () => {
    try {
        console.log(`初始化房源编辑表单, ID: ${homestayId.value}`);
        loading.value = true;
        const response = await getHomestayById(Number(homestayId.value));

        // --- 调试日志：检查 API 返回的原始数据 ---
        console.log('API 返回的房源详情原始数据:', JSON.stringify(response.data, null, 2));

        const homestayData = response.data;

        if (homestayData) {
            Object.assign(homestayForm, homestayData);

            // --- 添加日志：检查加载后的 type 值 ---
            console.log('[initEditForm] After Object.assign, homestayForm.type:', homestayForm.type);
            // --- 日志结束 ---

            // --- 调试日志：检查待回显的 Code --- 
            console.log('待回显的地址编码:', {
                provinceCode: homestayData.provinceCode,
                cityCode: homestayData.cityCode,
                districtCode: homestayData.districtCode
            });

            if (homestayData.provinceCode && homestayData.cityCode) {
                const codes = [homestayData.provinceCode, homestayData.cityCode];
                if (homestayData.districtCode) {
                    codes.push(homestayData.districtCode);
                }
                selectedAreaCodes.value = codes;
                // --- 调试日志：检查设置给 Cascader 的值 ---
                console.log('已设置 selectedAreaCodes.value:', JSON.stringify(selectedAreaCodes.value));
            } else {
                selectedAreaCodes.value = [];
                console.log('省份或城市编码缺失，清空 selectedAreaCodes.value');
            }

            // ... (其他字段处理保持不变) ...
            homestayForm.price = String(homestayData.price || 0);
            if (homestayData.amenities && Array.isArray(homestayData.amenities)) { homestayForm.amenities = [...homestayData.amenities]; } else { homestayForm.amenities = []; }
            if (homestayData.images && Array.isArray(homestayData.images)) { const validImages = homestayData.images.filter((url: any) => url != null && url !== ''); homestayForm.images = [...validImages]; fileList.value = validImages.map((url: string) => ({ name: url.substring(url.lastIndexOf('/') + 1), url })); } else { homestayForm.images = []; fileList.value = []; }

            // 如果 amenities 仍为空，尝试单独加载设施（兼容部分后端返回结构）
            if (!homestayForm.amenities || homestayForm.amenities.length === 0) {
                console.log('房源设施数据为空，尝试单独加载设施');
                try {
                    const { getHomestayAmenitiesApi } = await import('@/api/amenities');
                    const amenitiesResponse = await getHomestayAmenitiesApi(Number(homestayId.value));
                    if (amenitiesResponse.data && Array.isArray(amenitiesResponse.data)) {
                        homestayForm.amenities = amenitiesResponse.data;
                    } else if (amenitiesResponse.data && amenitiesResponse.data.data && Array.isArray(amenitiesResponse.data.data)) {
                        homestayForm.amenities = amenitiesResponse.data.data;
                    }
                } catch (amenitiesError) {
                    console.error('单独加载设施失败:', amenitiesError);
                }
            }

        } else {
            throw new Error('未获取到房源数据');
        }

    } catch (error) {
        console.error('初始化房源编辑表单失败:', error);
    } finally {
        loading.value = false;
    }
}

// 添加加载草稿函数
const loadDraft = () => {
    console.log('尝试加载草稿数据');
    try {
        const draftData = localStorage.getItem('homestayDraft'); // 与 autoSaveDraft 保持一致
        if (draftData) {
            const parsed = JSON.parse(draftData);
            console.log('找到表单草稿数据:', parsed);

            // 合并草稿数据到表单
            Object.assign(homestayForm, parsed);

            // **处理草稿地址回显**
            if (parsed.provinceCode && parsed.cityCode) {
                const codes = [parsed.provinceCode, parsed.cityCode];
                if (parsed.districtCode) {
                    codes.push(parsed.districtCode);
                }
                selectedAreaCodes.value = codes; // 设置级联选择器的值
                console.log('草稿地址回显，设置 selectedAreaCodes:', codes);
            } else {
                selectedAreaCodes.value = []; // 清空
            }

            lastSaved.value = new Date(parsed._lastSaved || Date.now()); // 注意草稿保存的时间字段名
            ElMessage.success('已恢复上次编辑的草稿');
        } else {
            console.log('没有找到草稿数据');
            // 设置默认值
            homestayForm.status = 'DRAFT';
            homestayForm.maxGuests = 1;
            homestayForm.minNights = 1;
        }
    } catch (e) {
        console.error('加载草稿失败:', e);
        ElMessage.warning('加载草稿失败，将使用默认值');
    }
};

// 获取位置文本的辅助函数 (修改为使用 codeToText)
const getLocationText = () => {
    const parts = [];
    if (homestayForm.provinceCode && codeToText[homestayForm.provinceCode]) {
        parts.push(codeToText[homestayForm.provinceCode]);
    }
    if (homestayForm.cityCode && codeToText[homestayForm.cityCode]) {
        parts.push(codeToText[homestayForm.cityCode]);
    }
    // 区县编码可能是可选的，并且可能没有对应的文本（比如直辖市的市辖区可能只有两级）
    if (homestayForm.districtCode && codeToText[homestayForm.districtCode]) {
        parts.push(codeToText[homestayForm.districtCode]);
    }
    // 添加详细地址，确保它存在且不为空
    if (homestayForm.addressDetail && homestayForm.addressDetail.trim()) {
        parts.push(homestayForm.addressDetail.trim());
    }

    // 如果没有任何部分，返回默认文本
    if (parts.length === 0) {
        return '位置未指定';
    }

    // 用空格连接各个部分
    return parts.join(' ');
};

// 添加验证上传文件的函数
// 添加这个函数到onMounted之前的位置
// 检查当前用户是否有权限编辑此房源
const checkEditPermission = async (homestayId: number) => {
    try {
        console.log(`检查用户是否有权限编辑房源，ID：${homestayId}`);

        // 获取房源详情
        const { getHomestayById } = await import('@/api/homestay');
        const response = await getHomestayById(homestayId);

        if (!response || !response.data) {
            ElMessage.error('找不到此房源信息');
            router.push('/host/homestay');
            return false;
        }

        // 获取当前用户
        let currentUser;
        try {
            currentUser = JSON.parse((localStorage.getItem('homestay_user') || localStorage.getItem('userInfo')) || '{}');
        } catch (e) {
            console.error('解析用户信息失败', e);
            router.push('/login');
            return false;
        }

        // 检查当前用户是否是房源所有者
        if (response.data.ownerUsername !== currentUser.username) {
            console.error(`权限不匹配: 当前用户=${currentUser.username}, 房源所有者=${response.data.ownerUsername}`);
            ElMessage.error('您没有权限编辑此房源');
            router.push('/host/homestay');
            return false;
        }

        console.log('权限验证通过，允许编辑');
        return true;
    } catch (error) {
        console.error('权限检查失败:', error);
        return false;
    }
};

// 监听级联选择器的变化，更新 homestayForm 中的编码字段
watch(selectedAreaCodes, (newVal) => {
    if (newVal && newVal.length === 3) {
        homestayForm.provinceCode = newVal[0];
        homestayForm.cityCode = newVal[1];
        homestayForm.districtCode = newVal[2];
    } else {
        // 如果选择不完整或清空，也清空对应 code
        homestayForm.provinceCode = '';
        homestayForm.cityCode = '';
        homestayForm.districtCode = '';
    }
    // 手动触发表单项验证，确保清除 "请选择所在地区" 的提示
    // 使用 nextTick 确保在 DOM 更新和数据赋值完成后执行
    nextTick(() => {
        if (formRef.value) {
            formRef.value.validateField('provinceCode', () => { }); // 添加空回调避免控制台警告
        }
    });
});

// 级联选择器 change 事件处理 (可选，如果需要在选择后立即做某事)
const handleAreaChange = (value: string[]) => {
    console.log('地区选择变化:', value);
    // 这里通常不需要额外操作，watch 已经处理了 code 的更新
    // 如果需要根据编码获取文本，可以在这里使用 codeToText
};

// 处理确认模式变化
const handleConfirmModeChange = (value: boolean) => {
    console.log('确认模式变化:', value ? '自动确认' : '房东确认');
    
    // 根据价格给出建议
    const currentPrice = Number(homestayForm.price);
    if (value && currentPrice > 500) {
        ElMessageBox.confirm(
            '您的房源价格较高（¥' + currentPrice + '/晚），建议使用房东确认制以便筛选客人。是否确定使用自动确认模式？',
            '价格提醒',
            {
                confirmButtonText: '确定使用自动确认',
                cancelButtonText: '改为房东确认',
                type: 'warning',
            }
        ).catch(() => {
            // 用户取消，改回房东确认制
            homestayForm.autoConfirm = false;
        });
    } else if (!value && currentPrice <= 300) {
        ElMessage.info('您的房源价格适中，使用自动确认可以提高预订转化率');
    }
};
</script>

<style scoped>
.homestay-form-container {
    padding: 20px;
    display: flex;
    gap: 20px;
}

.form-card {
    margin-bottom: 20px;
    flex: 1;
}

.preview-panel {
    width: 300px;
    background-color: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    padding: 15px;
    position: sticky;
    top: 20px;
    max-height: calc(100vh - 40px);
    overflow-y: auto;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.form-progress {
    margin-bottom: 30px;
}

.form-section {
    margin-bottom: 30px;
    padding-bottom: 20px;
    border-bottom: 1px solid #ebeef5;
}

.form-section h3 {
    margin-bottom: 20px;
    font-weight: 600;
    color: #303133;
    display: flex;
    align-items: center;
}

.section-subtitle {
    font-size: 14px;
    color: #909399;
    font-weight: normal;
    margin-left: 10px;
}

.upload-container {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    text-align: center;
}

.upload-icon {
    font-size: 28px;
    color: #8c939d;
    width: 178px;
    height: 60px;
    line-height: 60px;
    margin: 0 auto;
}

.upload-text {
    color: #606266;
    font-size: 14px;
    text-align: center;
    margin-bottom: 15px;
}

.upload-image {
    width: 178px;
    height: 178px;
    display: block;
    object-fit: cover;
}

.form-step-actions {
    display: flex;
    justify-content: space-between;
    margin-top: 30px;
    padding-top: 20px;
    border-top: 1px solid #ebeef5;
    align-items: center;
}

.upload-wrapper {
    position: relative;
}

.preview-container {
    position: relative;
}

.preview-image {
    width: 240px;
    height: 160px;
    display: block;
    object-fit: cover;
    border-radius: 6px;
    border: 1px solid #d9d9d9;
}

.image-actions {
    position: absolute;
    top: 5px;
    right: 5px;
    background-color: rgba(255, 255, 255, 0.7);
    border-radius: 50%;
    padding: 0;
}

.upload-tip {
    margin-top: 10px;
    font-size: 12px;
    color: #909399;
}

.gallery-container {
    display: flex;
    flex-direction: column;
    gap: 15px;
}

.gallery-images {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.gallery-item {
    position: relative;
    width: 160px;
    height: 160px;
}

.gallery-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 6px;
    border: 1px solid #d9d9d9;
}

.gallery-upload {
    margin-top: 10px;
}

.field-tips {
    margin-top: 8px;
    font-size: 13px;
    color: #606266;
    background-color: #f5f7fa;
    padding: 10px;
    border-radius: 4px;
}

.tip-title {
    font-weight: bold;
    margin-bottom: 5px;
}

.field-tips ul {
    margin: 5px 0;
    padding-left: 20px;
}

.examples {
    margin-top: 8px;
    font-style: italic;
}

.example-item {
    color: #409EFF;
}

.price-input-container {
    display: flex;
    align-items: center;
}

.price-symbol {
    font-size: 16px;
    margin-right: 5px;
    color: #606266;
}

.price-assistant {
    margin-top: 10px;
    font-size: 13px;
    background-color: #f0f9eb;
    padding: 10px;
    border-radius: 4px;
}

.market-insight {
    margin-bottom: 8px;
}

.insight-title {
    font-weight: bold;
    color: #67c23a;
}

.pricing-tips ul {
    margin: 5px 0;
    padding-left: 20px;
}

.guests-nights-container {
    display: flex;
    gap: 20px;
}

.half-width {
    width: 50%;
}

.selector-description {
    font-size: 12px;
    color: #909399;
    margin-top: 5px;
}

.field-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 5px;
}

.description-guide {
    margin-bottom: 15px;
    padding: 10px;
    background-color: #ecf5ff;
    border-radius: 4px;
    font-size: 14px;
    color: #409EFF;
}

.description-tips {
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;
}

.tips-title {
    font-weight: bold;
}

.form-completion {
    margin-top: 20px;
}

.completion-text {
    font-size: 14px;
    margin-bottom: 5px;
    color: #606266;
}

.preview-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 15px;
    padding-bottom: 10px;
}

.preview-image img {
    width: 100%;
    height: 150px;
    object-fit: cover;
    border-radius: 8px;
}

.preview-info {
    padding: 10px 0;
}

.preview-info h4 {
    margin: 0 0 8px 0;
    font-size: 16px;
}

.preview-type {
    font-size: 13px;
    color: #909399;
    margin-bottom: 5px;
}

.preview-price {
    font-size: 16px;
    font-weight: bold;
    color: #f56c6c;
    margin-bottom: 8px;
}

.preview-location {
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
}

.preview-highlights {
    font-size: 13px;
    color: #606266;
    background-color: #f5f7fa;
    padding: 8px;
    border-radius: 4px;
}

.amenities-tip {
    margin-bottom: 15px;
    font-size: 14px;
    color: #606266;
}

.upload-guide {
    background-color: #f5f7fa;
    padding: 10px;
    border-radius: 4px;
    margin-bottom: 15px;
}

.upload-guide ul {
    margin: 5px 0;
    padding-left: 20px;
}

.cover-upload-guide {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px;
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    width: 240px;
}

.image-upload-guide {
    margin-bottom: 15px;
    font-size: 14px;
    color: #606266;
}

.save-status {
    margin-left: auto;
    font-size: 12px;
    color: #909399;
    display: flex;
    align-items: center;
}

.save-status i {
    margin-right: 4px;
}

.select-empty-text {
    padding: 10px 0;
    text-align: center;
    color: #909399;
    font-size: 14px;
}

.upload-error-actions {
    display: flex;
    gap: 10px;
    margin-top: 10px;
}

.debug-info {
    margin-top: 10px;
    padding: 10px;
    background-color: #f5f7fa;
    border-radius: 4px;
    max-height: 300px;
    overflow-y: auto;
    font-family: monospace;
}

.debug-info pre {
    white-space: pre-wrap;
    word-break: break-all;
}

.debug-section h4 {
    margin-top: 0;
    margin-bottom: 8px;
    color: #606266;
    font-size: 14px;
}

.step-title {
    cursor: pointer;
    /* 添加鼠标指针样式 */
    user-select: none;
    /* 防止文本被选中 */
}

.step-title:hover {
    color: var(--el-color-primary);
    /* 鼠标悬停时改变颜色 */
}

/* 自动确认功能样式 */
.booking-confirm-container {
    background-color: #f8f9fa;
    padding: 15px;
    border-radius: 8px;
    border: 1px solid #e9ecef;
}

.confirm-mode-description {
    margin-top: 15px;
}

.auto-confirm-info,
.manual-confirm-info {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px;
    border-radius: 6px;
    background-color: #fff;
    border: 1px solid #e4e7ed;
}

.auto-confirm-info {
    border-left: 4px solid #67c23a;
}

.manual-confirm-info {
    border-left: 4px solid #409eff;
}

.info-icon {
    color: #909399;
    font-size: 18px;
    margin-top: 2px;
    flex-shrink: 0;
}

.info-content {
    flex: 1;
}

.info-content p {
    margin: 0 0 8px 0;
    font-size: 14px;
    color: #303133;
}

.info-content ul {
    margin: 0;
    padding-left: 16px;
    list-style: none;
}

.info-content li {
    margin: 4px 0;
    font-size: 13px;
    color: #606266;
    line-height: 1.4;
}

.info-content li:before {
    content: '';
    display: inline-block;
    width: 4px;
    height: 4px;
    background-color: currentColor;
    border-radius: 50%;
    margin-right: 8px;
    vertical-align: middle;
}
</style>
