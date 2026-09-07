<template>
  <div class="route-plan-list" v-if="routes && routes.length">
    <div class="rpl-title">可选路线（{{ routes.length }}）</div>
    <div
      v-for="route in routes"
      :key="route.routeId"
      class="rpl-card"
      :class="{ selected: selectedId === route.routeId }"
      @click="$emit('select', route)"
    >
      <div class="rpl-head">
        <span class="rpl-name">{{ route.name }}</span>
        <button type="button" class="rpl-select-btn" @click.stop="$emit('select', route)">
          {{ selectedId === route.routeId ? '已选择' : '选择' }}
        </button>
      </div>
      <div class="rpl-metrics">
        <span>{{ formatKm(route.distanceMeters) }}</span>
        <span>{{ formatDuration(route.durationSeconds) }}</span>
        <span v-if="route.trafficLights != null">红绿灯 {{ route.trafficLights }}</span>
        <span v-if="route.trafficSummary">路况 {{ route.trafficSummary }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  routes: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' }
})

defineEmits(['select'])

function formatKm(meters) {
  if (meters == null) return '—'
  return (meters / 1000).toFixed(1) + ' km'
}

function formatDuration(seconds) {
  if (seconds == null) return '—'
  const m = Math.round(seconds / 60)
  if (m < 60) return m + ' min'
  const h = Math.floor(m / 60)
  const mm = m % 60
  return h + 'h ' + mm + 'min'
}
</script>

<style scoped>
.route-plan-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 260px;
  max-width: 320px;
}
.rpl-title {
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 600;
}
.rpl-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.rpl-card:hover {
  border-color: #91d5ff;
}
.rpl-card.selected {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.15);
  background: #f0f9ff;
}
.rpl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.rpl-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}
.rpl-select-btn {
  border: 1px solid #1890ff;
  background: #fff;
  color: #1890ff;
  border-radius: 4px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
}
.rpl-card.selected .rpl-select-btn {
  background: #1890ff;
  color: #fff;
}
.rpl-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: #595959;
}
</style>
