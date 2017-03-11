#!/bin/sh
suc=0
fail=0
rm -rf TestResults
mkdir TestResults

suc=0
fail=0
echo "#!/bin/sh" > espressotestc
echo "java -cp bin/:src/Utilities/java_cup_runtime.jar Espressoc -I Include/ -ref j -P:6 \$1" >> espressotestc
chmod 755 espressotestc
for p in '' '+' ; do
    for javafile in `ls ../Tests/Phase6/Espresso$p/GoodTests/*.java` ; do
        rm -rf *.j
        echo "----------------------------------------------------------------------------"
       
        # compile source using the phase 6 Espresso compiler
        echo  "Compiling \033[1;32m$javafile\033[0m .... \c"
        ./espressotestc $javafile > /dev/null 2>&1
        echo "\033[1;32mdone\033[0m"
       
        rm -f Io.j
        # assemble all the jasmin files

        echo "Assembling \c"
        for file in `ls *.j` ; do
            echo "$file \c"    
            ./jasmin $file > /dev/null 2>&1
        done
        echo "\033[1;32mdone\033[0m"
            
        runfile=`grep -l "main(\[Ljava/lang/String;)V" *.j | cut -f 1 -d '.' `
        echo "Executing \033[1;32m$runfile\033[0m .... \c"
       
        l=`strings $javafile | head -1 | sed 's/\/\///g' | sed 's/(//g' | sed 's/)//g'`
        args=`cat $javafile | head -2 | tail -1 | sed 's/\/\///g' | sed 's/(//g' | sed 's/)//g'`
        echo $l
        ll=$((l+2))
        head -$ll $javafile | tail -$l | sed 's/\/\///g' > expected_output.txt
       
        #echo "java -cp Lib:. $runfile"

        echo $args | java -cp Lib:. $runfile > output.txt
        echo "done .... \c"
        diffcount=`diff -w output.txt expected_output.txt | wc -l | sed 's/ //g'`
        if [ $diffcount -eq 0 ] ; then 
            echo "\033[1;32mSucceeded\033[0m"
            suc=$((suc+1))
        else
            echo "\033[1;31mFailed\033[0m"
            diff -w output.txt expected_output.txt
            fail=$((fail+1))
        fi
    done
done
rm -rf espressotestc
rm -f output.txt
rm -f expected_output
rm -f *.j
for f in `ls *.class | grep -v Espressoc.class` ; do
  rm -f $f
done

echo "-------------------------------------"
echo "$suc tests \033[1;32msucceeded\033[0m"
echo "$fail tests \033[1;31mfailed\033[0m"
echo "====================================="

rm -r TestResults
rm expected_output.txt