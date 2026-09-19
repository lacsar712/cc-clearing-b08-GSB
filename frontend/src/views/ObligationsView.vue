<template>
  <div class="page">
    <h2 class="page-title">义务录入</h2>
    <p class="page-desc">录入应付义务并按币种/交割日/状态筛选；OPEN 义务可修订金额并留痕</p>

    <div class="card-panel" style="margin-bottom:16px">
      <el-form :model="form" label-width="110px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="付款方">
              <el-select v-model="form.payerMemberId" filterable style="width:100%" :disabled="!auth.isOperator">
                <el-option v-for="m in activeMembers" :key="m.memberId" :label="m.name" :value="m.memberId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收款方">
              <el-select v-model="form.payeeMemberId" filterable style="width:100%" :disabled="!auth.isOperator">
                <el-option v-for="m in activeMembers" :key="m.memberId" :label="m.name" :value="m.memberId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种">
              <el-input v-model="form.currency" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额">
              <el-input-number v-model="form.amount" :min="0.00000001" :precision="8" :controls="false" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交易日">
              <el-date-picker v-model="form.tradeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交割日">
              <el-date-picker v-model="form.settleDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-button type="primary" :disabled="!auth.isOperator" :loading="saving" @click="create">提交义务</el-button>
      </el-form>
    </div>

    <div class="toolbar">
      <el-select v-model="filters.currency" clearable placeholder="币种" style="width:120px">
        <el-option label="USD" value="USD" />
        <el-option label="CNY" value="CNY" />
        <el-option label="EUR" value="EUR" />
      </el-select>
      <el-date-picker v-model="filters.settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
      <el-select v-model="filters.status" clearable placeholder="状态" style="width:140px">
        <el-option v-for="s in ['OPEN','NETTED','SETTLED','CANCELLED']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="card-panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="obligationId" label="义务 ID" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.obligationId }}</span></template>
        </el-table-column>
        <el-table-column label="付款方" min-width="120">
          <template #default="{ row }">{{ nameOf(row.payerMemberId) }}</template>
        </el-table-column>
        <el-table-column label="收款方" min-width="120">
          <template #default="{ row }">{{ nameOf(row.payeeMemberId) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column prop="amount" label="金额" width="140" />
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.isOperator && row.status === 'OPEN'"
              size="small"
              type="primary"
              link
              @click="openRevise(row)"
            >修改金额</el-button>
            <el-button size="small" link type="info" @click="openRevisions(row)">变更记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="reviseDialog" title="修改金额" width="420px">
      <el-form v-if="reviseRow" label-width="110px" @submit.prevent>
        <el-form-item label="义务 ID">
          <span class="mono" style="font-size:12px">{{ reviseRow.obligationId }}</span>
        </el-form-item>
        <el-form-item label="当前金额">
          <span class="mono">{{ reviseRow.amount }} {{ reviseRow.currency }}</span>
        </el-form-item>
        <el-form-item label="新金额">
          <el-input-number
            v-model="reviseAmount"
            :min="0.00000001"
            :precision="8"
            :controls="false"
            style="width:100%"
            placeholder="请输入正数金额"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviseDialog = false">取消</el-button>
        <el-button type="primary" :loading="revising" @click="submitRevise">确认修改</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="revisionDrawer" title="金额变更记录" size="480px">
      <div v-if="revisionRow" style="margin-bottom:12px">
        <span class="mono" style="font-size:12px">{{ revisionRow.obligationId }}</span>
        <el-tag style="margin-left:8px">{{ revisionRow.status }}</el-tag>
      </div>
      <el-table :data="revisions" v-loading="revisionsLoading" stripe empty-text="暂无变更记录">
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.revisedAt) }}</template>
        </el-table-column>
        <el-table-column label="旧值 → 新值" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.oldAmount }} → {{ row.newAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作员" width="100" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const members = ref([])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const today = new Date().toISOString().slice(0, 10)

const reviseDialog = ref(false)
const reviseRow = ref(null)
const reviseAmount = ref(null)
const revising = ref(false)

const revisionDrawer = ref(false)
const revisionRow = ref(null)
const revisions = ref([])
const revisionsLoading = ref(false)

const form = reactive({
  payerMemberId: '',
  payeeMemberId: '',
  currency: 'USD',
  amount: 10000,
  tradeDate: today,
  settleDate: today
})

const filters = reactive({
  currency: 'USD',
  settleDate: today,
  status: 'OPEN'
})

const activeMembers = computed(() => members.value.filter((m) => m.status === 'ACTIVE'))
const memberMap = computed(() => Object.fromEntries(members.value.map((m) => [m.memberId, m.name])))

function nameOf(id) {
  return memberMap.value[id] || id
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

async function loadMembers() {
  const { data } = await api.get('/members')
  members.value = data
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filters.currency) params.currency = filters.currency
    if (filters.settleDate) params.settleDate = filters.settleDate
    if (filters.status) params.status = filters.status
    const { data } = await api.get('/obligations', { params })
    rows.value = data
  } finally {
    loading.value = false
  }
}

async function create() {
  saving.value = true
  try {
    await api.post('/obligations', { ...form })
    ElMessage.success('义务已录入')
    await load()
  } finally {
    saving.value = false
  }
}

function openRevise(row) {
  if (!auth.isOperator || row.status !== 'OPEN') {
    ElMessage.error('仅 OPEN 状态的义务可修改金额')
    return
  }
  reviseRow.value = row
  reviseAmount.value = Number(row.amount)
  reviseDialog.value = true
}

async function submitRevise() {
  const value = Number(reviseAmount.value)
  if (reviseAmount.value === null || reviseAmount.value === undefined || Number.isNaN(value) || !Number.isFinite(value)) {
    ElMessage.error('金额格式错误，请输入合法数字')
    return
  }
  if (value <= 0) {
    ElMessage.error('金额必须为正数')
    return
  }
  if (!reviseRow.value || reviseRow.value.status !== 'OPEN') {
    ElMessage.error('仅 OPEN 状态的义务可修改金额')
    reviseDialog.value = false
    return
  }
  revising.value = true
  try {
    const { data } = await api.put(`/obligations/${reviseRow.value.obligationId}/amount`, { amount: value })
    const idx = rows.value.findIndex((r) => r.obligationId === data.obligationId)
    if (idx >= 0) rows.value[idx] = data
    ElMessage.success('金额已修改并留痕')
    reviseDialog.value = false
    if (revisionDrawer.value && revisionRow.value?.obligationId === data.obligationId) {
      await loadRevisions(data.obligationId)
    }
  } finally {
    revising.value = false
  }
}

function openRevisions(row) {
  revisionRow.value = row
  revisionDrawer.value = true
  loadRevisions(row.obligationId)
}

async function loadRevisions(obligationId) {
  revisionsLoading.value = true
  try {
    const { data } = await api.get(`/obligations/${obligationId}/revisions`)
    revisions.value = data
  } finally {
    revisionsLoading.value = false
  }
}

onMounted(async () => {
  await loadMembers()
  await load()
})
</script>
