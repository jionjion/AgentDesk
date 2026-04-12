import {cva, type VariantProps} from 'class-variance-authority'

export {ToastProvider} from 'reka-ui'
export {default as Toast} from './Toast.vue'
export {default as ToastTitle} from './ToastTitle.vue'
export {default as ToastDescription} from './ToastDescription.vue'
export {default as ToastAction} from './ToastAction.vue'
export {default as ToastClose} from './ToastClose.vue'
export {default as ToastViewport} from './ToastViewport.vue'
export {default as Toaster} from './Toaster.vue'
export {useToast} from './use-toast'

export const toastVariants = cva(
    'group pointer-events-auto absolute top-0 left-0 right-0 flex w-full items-center justify-between space-x-2 overflow-hidden rounded-md border p-4 pr-6 shadow-lg transition-all duration-300 ease-in-out data-[swipe=cancel]:translate-x-0 data-[swipe=end]:translate-x-[var(--reka-toast-swipe-end-x)] data-[swipe=move]:translate-x-[var(--reka-toast-swipe-move-x)] data-[swipe=move]:transition-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[swipe=end]:animate-out data-[state=closed]:fade-out-80 data-[state=closed]:slide-out-to-right-full data-[state=open]:slide-in-from-top-full',
    {
        variants: {
            variant: {
                default: 'border bg-background text-foreground',
                success: 'success group border-green-500/50 bg-green-50 text-green-900 dark:bg-green-950 dark:text-green-100',
                destructive: 'destructive group border-destructive bg-destructive text-destructive-foreground',
                warning: 'warning group border-yellow-500/50 bg-yellow-50 text-yellow-900 dark:bg-yellow-950 dark:text-yellow-100',
            },
        },
        defaultVariants: {
            variant: 'default',
        },
    },
)

export type ToastVariants = VariantProps<typeof toastVariants>
