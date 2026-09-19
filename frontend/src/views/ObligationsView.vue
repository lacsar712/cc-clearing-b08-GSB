<template>
  <div class="page">
    <h2 class="page-title">义务录入</h2>
    <p class="page-desc">录入应付义务并按币种/交割日/状态筛选；仅 OPEN 义务可由操作员修订金额，每次修订留存变更痕迹</p>

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
      <el-table ref="tableRef" :data="rows" v-loading="loading" stripe row-key="obligationId"
                @expand-change="onExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding:8px 24px">
              <strong>金额变更记录</strong>
              <div v-loading="revisionsLoading[row.obligationId]" style="margin-top:8px">
                <el-table :data="revisionsMap[row.obligationId] || []" size="small" border>
                  <el-table-column prop="oldAmount" label="旧金额" width="160" />
                  <el-table-column prop="newAmount" label="新金额" width="160" />
                  <el-table-column prop="operator" label="操作员" width="160" />
                  <el-table-column label="变更时间" min-width="220">
                    <template #default="{ row: r }">{{ formatTime(r.revisedAt) }}</template>
                  </el-table-column>
                </el-table>
                <el-text v-if="!(revisionsLoading[row.obligationId]) && !(revisionsMap[row.obligationId] || []).length"
                         type="info" size="small" style="display:inline-block;margin-top:6px">
                  暂无金额变更记录
                </el-text>
              </div>
            </div>
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.isOperator && row.status === 'OPEN'"
                       link type="primary" @click="openEdit(row)">修改金额</el-button>
            <el-text v-else type="info" size="small">不可修改</el-text>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" title="修订义务金额" width="420px" @closed="resetForm">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px" @submit.prevent>
        <el-form-item label="义务 ID">
          <span class="mono">{{ editForm.obligationId }}</span>
        </el-form-item>
        <el-form-item label="当前金额">
          <span>{{ editForm.oldAmount }} {{ editForm.currency }}</span>
        </el-form-item>
        <el-form-item label="新金额" prop="amountText">
          <el-input v-model="editForm.amountText" placeholder="请输入正数，最多 8 位小数" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRevision" @click="submitRevision">确认修改</el-button>
      </template>
    </el-dialog>
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

const tableRef = ref()
const revisionsMap = reactive({})
const revisionsLoading = reactive({})

const editVisible = ref(false)
const savingRevision = ref(false)
const editFormRef = ref()
const editForm = reactive({
  obligationId: '',
  currency: '',
  oldAmount: '',
  amountText: ''
})

const editRules = {
  amountText: [
    { required: true, message: '金额不能为空', trigger: 'blur' },
    {
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        const text = String(value ?? '').trim()
        if (!/^\d+(\.\d{1,8})?$/.test(text)) {
          return callback(new Error('金额格式错误：请输入正数，最多 8 位小数'))
        }
        const n = Number(text)
        if (!Number.isFinite(n) || n <= 0) {
          return callback(new Error('金额必须为大于 0 的正数'))
        }
        callback()
      }
    }
  ]
}

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

function formatTime(instant) {
  if (!instant) return ''
  const d = new Date(instant)
  return Number.isNaN(d.getTime()) ? instant : d.toLocaleString()
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

function openEdit(row) {
  editForm.obligationId = row.obligationId
  editForm.currency = row.currency
  editForm.oldAmount = row.amount
  editForm.amountText = String(row.amount)
  editVisible.value = true
}

function resetForm() {
  editFormRef.value?.clearValidate()
}

async function submitRevision() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingRevision.value = true
  try {
    const { data } = await api.patch(
      `/obligations/${editForm.obligationId}/amount`,
      // send as string to preserve exact decimal precision when binding to BigDecimal
      { amount: editForm.amountText.trim() }
    )
    ElMessage.success('金额已修订，变更记录已留存')
    editVisible.value = false
    // 列表立即反映新金额
    const idx = rows.value.findIndex((r) => r.obligationId === data.obligationId)
    if (idx >= 0) rows.value[idx] = data
    // 已展开过的变更痕迹立即刷新
    if (revisionsMap[editForm.obligationId]) {
      await loadRevisions(editForm.obligationId)
    }
  } finally {
    savingRevision.value = false
  }
}

async function loadRevisions(obligationId) {
  revisionsLoading[obligationId] = true
  try {
    const { data } = await api.get(`/obligations/${obligationId}/revisions`)
    revisionsMap[obligationId] = data
  } finally {
    revisionsLoading[obligationId] = false
  }
}

async function onExpandChange(row, expandedRows) {
  const expanded = Array.isArray(expandedRows)
    ? expandedRows.some((r) => r.obligationId === row.obligationId)
    : !!expandedRows
  if (expanded && !revisionsMap[row.obligationId]) {
    await loadRevisions(row.obligationId)
  }
}

onMounted(async () => {
  await loadMembers()
  await load()
})
</script>
