________________________________________
Geysermen Project
________________________________________
Geysermen is a smart geyser management system designed to:

•	Monitor solar PV, battery, grid, and load data from a Deye hybrid inverter.

•	Adaptively control two 150L geysers based on real-time available solar power.

•	Minimize grid power usage, maximizing solar utilization.

•	Provide live data and control via a custom Android dashboard app.

•	Log system activity for historical analysis and future optimization.


•	Predict solar production using machine learning to optimize heating windows.
________________________________________

🚀 Current System Features

ESP32-C6 Geyser Controller:

  •	Connects to the inverter using Solarman V5 TCP Modbus protocol.

  •	Reads PV generation, battery charge level, grid import/export values.

  •	Controls geyser relay switches via TCP socket commands.

  •	Logs temperature readings, inverter status, and geyser activity to local SPIFFS filesystem.

  •	(Upcoming) Machine learning prediction module to forecast solar production.

Android Mobile App (Built using Meerkat IDE, in Kotlin):

  •	Displays real-time PV, battery, grid, UPS, and geyser states.

  •	Toggle controls to manually switch geysers ON/OFF.

  •	Background TCP listener service for inverter overload alerts.

  •	Responsive dashboard design for both portrait and landscape layouts.

Smart Load Management:

  •	PWM-based geyser load control (planned).

  •	Adaptive switching logic based on excess PV availability and battery level.
________________________________________
🛠 Technologies Used

Technology	Purpose

ESP32-C6 + ESP-IDF	Embedded smart controller

MicroPython	Prototype controller logic

Android App (Kotlin)	Mobile interface

MQTT (planned)	Future cloud monitoring

Solarman V5 Protocol	Inverter communication

SPIFFS	On-device storage for logs/configs

TCP Sockets	App ⇄ ESP32 real-time communication

Machine Learning (planned)	Solar production prediction
________________________________________
🧠 Project History

Stage	Description

🔹 Start	Initial manual geyser control idea to optimize solar energy use

🔹 ESP32 MicroPython Prototype	Basic TCP server, PV/grid monitoring, geyser control

🔹 Android App v1	Simple TCP app for manual ON/OFF geyser control

🔹 ESP32 C++ Full Rewrite	Full system rewrite under ESP-IDF for stability and multithreading

🔹 Android App v2	Professional UI, background services, real-time alerts

🔹 Integration	Master/Slave ESP32 setup, multi-device TCP communication

🔹 Machine Learning Phase (Upcoming)	Predictive control based on forecasted PV generation
________________________________________
📈 Future Plans

•	Full PWM-based geyser load modulation to smoothly match PV excess.

•	Cloud connectivity via MQTT and remote logging/alerts.

•	Expand system to control additional smart loads (pool pumps, battery boost, etc).

•	AI-enhanced optimization based on weather prediction and past patterns.
________________________________________
📂 Project Structure

css

CopyEdit

Geysermen/

├── ESP32_C6_Controller/

│   ├── main.cpp

│   ├── logging.cpp

│   ├── modbus_client.cpp

│   └── config.json (SPIFFS)

├── Android_App/

│   ├── app/

│   │   ├── src/

│   │   ├── res/

│   │   └── manifest/

│   └── build.gradle

├── Documentation/

│   ├── README.md

│   └── WiringDiagrams/

└── LICENSE
________________________________________
📃 License

This project is licensed under the MIT License – see the LICENSE file for details.
________________________________________
🙏 Acknowledgments

Special thanks to:

•	The ESP32 and ESP-IDF communities.

•	Solarman Open Protocol contributors.

•	Android Kotlin tutorials and open-source libraries.

•	The incredible testing and debugging over many days to make the system reliable!
________________________________________
📬 Contact

If you have questions, ideas, or want to contribute:

•	GitHub: @pietermullertrust

