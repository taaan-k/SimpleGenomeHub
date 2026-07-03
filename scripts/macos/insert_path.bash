if [ -f ~/.bash_profile ]; then
	cp ~/.bash_profile ~/.bash_profile.bak
	echo export PATH=`pwd`:\$PATH >> ~/.bash_profile
else
	cp ~/.profile ~/.profile.bak
	echo export PATH=`pwd`:\$PATH >> ~/.profile
fi
