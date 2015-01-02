rastemp
=======

Raspberry Pi Temp&RH Monitoring system

Install:

cd ~
git clone https://github.com/IS1/rastemp.git

cd rastemp

sudo ./rastemp

*To start rastemp on boot

sudo nano /etc/rc.local

Put this between "fi" and "exit 0":

/home/pi/rastemp/rastemp

Android app source also available in directory "Android". (Android Studio Project)

** Android app language is korean.
