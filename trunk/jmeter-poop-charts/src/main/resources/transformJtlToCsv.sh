grep "\<httpSample " | sed -e 's/<httpSample t=//g'  | sed -e 's/lt=//g' | sed -e 's/lb=//g' | sed -e 's/ts=//' | sed -e 's/s=//g' | sed -e 's/rc=//g' | sed -e 's/rc=//g' | sed -e 's/rm=//g' | sed -e 's/tn=//g' | sed -e 's/dt=//g' | sed -e 's/by=//g' | sed -e 's/>//g' | sed -e 's/^"//g' | sed -e 's/"$//g' | sed -e 's/^ //' | sed -e 's/^ //' | sed -e 's/" "/,/g' | sed -e 's/^"//g' | sed -e 's/"$//g' | sed -e 's/"\///g'