import { ref, type Component } from 'vue'

const TOAST_LIMIT = 5
const TOAST_REMOVE_DELAY = 1000

export type ToastVariant = 'default' | 'success' | 'destructive' | 'warning'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface Toast {
  id: string
  title?: string
  description?: string
  variant?: ToastVariant
  duration?: number
  action?: ToastAction
  open: boolean
}

let count = 0
function genId() {
  count = (count + 1) % Number.MAX_SAFE_INTEGER
  return count.toString()
}

const toasts = ref<Toast[]>([])
const toastsToRemove = new Map<string, ReturnType<typeof setTimeout>>()

function addToRemoveQueue(id: string) {
  if (toastsToRemove.has(id)) return

  const timeout = setTimeout(() => {
    toastsToRemove.delete(id)
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }, TOAST_REMOVE_DELAY)

  toastsToRemove.set(id, timeout)
}

function dismiss(id?: string) {
  toasts.value = toasts.value.map((t) =>
    t.id === id || id === undefined ? { ...t, open: false } : t
  )

  if (id) {
    addToRemoveQueue(id)
  } else {
    toasts.value.forEach((t) => addToRemoveQueue(t.id))
  }
}

type ToastInput = Omit<Toast, 'id' | 'open'>

function toast(props: ToastInput) {
  const id = genId()

  toasts.value = [{ ...props, id, open: true }, ...toasts.value].slice(0, TOAST_LIMIT)

  return { id, dismiss: () => dismiss(id) }
}

export function useToast() {
  return {
    toasts,
    toast,
    dismiss,
  }
}
