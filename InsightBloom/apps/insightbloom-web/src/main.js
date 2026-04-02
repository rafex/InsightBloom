import { createApp } from 'vue'
import App from './App.vue'
import router from './app/router'
import 'animate.css'
import './styles/global.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
