# SmartVision
## This project is a mobile assistant created for Visually Impaired/Challenged individuals.
## The various use case of this app are given below :
### 1. Detect Objects   
### 2. Scan Products
### 3. Summarize Docs
### 4. Reading Mode
### 5. Detect Faces

## 1. For proper functioning of the detect objects and summarize docs feature, set your Gemini API Key in the local.properties file that is create a variable apiKey="Enter-your-API-Key-here". You need to enter the gemini API key as the value for the apiKey.(Also, if you get gradle build errors then consider rebuilding or cleaning up the project after adding API key in the local.properties file)
## 2. For Detect objects feature a WiFi enabled camera is required like ESP32 cam module. Also, The ESP32 cam module and the mobile phone should be in the same network. So, for convenience turn on the mobile hotspot of the phone and connect the ESP32 camera module to the hotspot network.
## 3. Once camera module is connected to the hotspot, figure out the IP address of the cam module and feed the image url(for example 192.168.223.149/jpg) in the setting screen of the app.(You can go to settings screen using the navigation drawer/pane)
## 4. For using the summarize documents feature, click on the summarize docs option from the dashboard and select a pdf document from the device memory. Note that a prompt text is already set for convenience to summarize the selected pdf document in 150words.
## 5. In case of any queries please reach out to me on my email Id(karthikr1493@gmail.com)

## Blog post link :  https://medium.com/@karthikr1493/smartvision-ai-enabled-mobile-assistant-for-visually-impaired-challenged-individuals-061bc5f5260a

