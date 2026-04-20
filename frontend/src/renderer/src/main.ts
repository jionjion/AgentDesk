import {createApp} from 'vue'
import {createPinia} from 'pinia'
import VueViewer from 'v-viewer'
import 'viewerjs/dist/viewer.css'
import './assets/main.css'
import './styles/markdown.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueViewer)

app.mount('#app')
