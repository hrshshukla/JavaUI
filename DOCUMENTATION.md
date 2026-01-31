### Run the app
```bash
cd ~/Documents/JavaUI

mvn clean compile exec:java -Dexec.mainClass=App
```

### Create JAR
```bash
mvn clean package
```


### Kill Ghost process 
```bash
 ps aux | grep java
```