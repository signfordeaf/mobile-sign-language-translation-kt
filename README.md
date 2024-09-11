# SignForDeaf Mobile Sign Language

## 🛠️ Install
[![](https://jitpack.io/v/signfordeaf/mobile-sign-language-translation-kt.svg)](https://jitpack.io/#signfordeaf/mobile-sign-language-translation-kt)
  
 Step 1. Add the JitPack repository to your build file (settings.gradle)
```gradle
dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url 'https://jitpack.io' }
		}
	}
```
  Step 2. Add the dependency
```gradle
dependencies {
	        implementation 'com.github.signfordeaf:mobile-sign-language-translation-kt:1.0.3'
	}
```

## 🧑🏻💻 Usage

###  📄Kotlin Class
   Call the following code in the onCreate function of your Kotlin classes, in which AppCompatActivity is included.
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        SignForDeafUtil.initialize("YOUR-API-KEY", requestUrl = "YOUR-REQUEST-URL")
        SignForDeafTranslate.makeAllTextSelectable(this)
    }
}
```
![Image](https://imgur.com/L3uxMEa.png)
