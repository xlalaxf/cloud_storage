<script setup>
import { Folder } from '@lucide/vue'

defineProps({
  node: { type: Object, required: true },
  level: { type: Number, required: true },
  selectedId: { type: Number, default: null },
  expandedIds: { type: Object, required: true },
  disabledChecker: { type: Function, required: true },
})

defineEmits(['toggle', 'select'])

function hasChildren(node) {
  return Boolean(node.children && node.children.length)
}
</script>

<template>
  <div>
    <button
      type="button"
      class="folder-tree-row"
      :class="{ active: selectedId === node.id, disabled: disabledChecker(node) }"
      :style="{ paddingLeft: `${12 + level * 18}px` }"
      :disabled="disabledChecker(node)"
      @click="$emit('select', node.id); hasChildren(node) ? $emit('toggle', node) : null"
    >
      <span
        class="folder-tree-toggle"
        :class="{ empty: !hasChildren(node) }"
        @click.stop="hasChildren(node) ? $emit('toggle', node) : null"
      >
        {{ hasChildren(node) ? (expandedIds.has(node.id) ? '▾' : '▸') : '' }}
      </span>
      <Folder :size="18" />
      <span>{{ node.name }}</span>
    </button>
    <template v-if="expandedIds.has(node.id)">
      <FolderTreeNode
        v-for="child in node.children || []"
        :key="child.id"
        :node="child"
        :level="level + 1"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        :disabled-checker="disabledChecker"
        @toggle="$emit('toggle', $event)"
        @select="$emit('select', $event)"
      />
    </template>
  </div>
</template>
