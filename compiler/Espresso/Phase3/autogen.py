import sys
import os
import string

#	Usage:
#		python autogen.py <argument>
#		Commands:
#			-clean
#			-gen
#			-diff
#			-verify
#			-runall
#
#		Only supports one argument
#		Example:
#		python autogen.py -runall
#


#	Important Notes:
#		User may change the variable definitions that follow after, 
#			if __name__ == '__main__':
#		the defaults will work though, but for further phases you will have to update the test directories
#		When declaring the variables do NOT include trailing / for directory assignments
#		DO NOT CHANGE THE OUTPUT* VARIABLES
#			If you must change them, be very careful because a rm -r is used to clean those folders.
#			If you specify a different folder you may delete all or most of your files.
#	
#		The verification of the diff files is very rudimentary.  
#		To check if a file passes the good tests it determines if there is any output in goodDiff.
#	 		if there is no output (ie whitespace) then the tests passed.
#		To check if a file passes the bad tests it will compare the error lines of the badDiff file.		
#			In bad diff, there will be the lines the errors where encountered and then the errors, ie:
#	
#				diff output_bad_C/IfTest.txt output_bad_CR/IfTest.txt
#				1c1
#				< /home/matt/CSC460/Espresso/CS460-Tests/Phase2/Espresso/BadTests/IfTest.java:4: Syntax error:
#				---
#				> /home/csgfms/public_html/Espresso/upload/IfTest.java.02-25-15:21:22:4: Syntax error:
#	
#			This program only strips all the "1c1" or [0-9]*c[0-9]* lines and compares them to make sure
#			the two numbers match... if they do the errors are generally generated due to the same error
#			MAKE SURE TO VERIFY THIS BY HAND, check to see if they are erring out for the correct reasons.
#		
#	       
#	 	Test files can contain ONLY 1 period
#	   



# FIX Verify file system structure
def verifyFileSystemStructure():
	"""docstring for verifyFileSystemStructure"""
	
	tmp = []	
	hasToExist = [good_test_location, bad_test_location, compiler1, compiler2]
	canCreate = [output_good_C, output_good_CR, output_bad_C, output_bad_CR]
	fullList = []
	fullList.extend(hasToExist)
	fullList.extend(canCreate)
	
	print "Verifying required files and directories..."
	for var in fullList:
		if var in hasToExist:
			if os.path.exists(var) is False:
				sys.exit("Can not verify that the required item exists: " + var + "\nTerminating Program.")
		if var in canCreate:
			if os.path.exists(var) is False:
				tmp.append(var)
	
	for l in tmp:
		print l + "/ Doesn't exist, creating it..."
		os.mkdir(l)
	
	print "Verification passed\n"
	return True


def clean():
	"""docstring for clean"""
	
	deleteable = [output_good_C, output_good_CR, output_bad_C, output_bad_CR, goodDiff, badDiff]
	tmp = []
	
	flag = False
	for f in deleteable:
		if os.path.exists(f):
			tmp.append(True)
		else:
			tmp.append(False)
	
	cleancmd = "rm -r "+output_good_C+"/ "+output_good_CR+"/ "+output_bad_C+"/ "+output_bad_CR+"/ "+goodDiff+" "+badDiff+" "+tmpCmp

	print "\nDelete files:"
	print "   " + output_good_C  + "/" 
	print "   " + output_good_CR + "/"
	print "   " + output_bad_C   + "/" 
	print "   " + output_bad_CR  + "/" 
	print "   " + goodDiff
	print "   " + badDiff 
	print "   " + tmpCmp
	print ""
	print "By command,"
	print cleancmd
	print ""

	uin = raw_input("Proceed (y/n)...   ").strip()

	if string.capwords(uin[0:1]) == "Y":
		os.popen(cleancmd)
	else:
		sys.exit("Files not deleted, program terminated.\n")
	

def generate():
	"""docstring for generate"""

	goodFiles = os.listdir(good_test_location)
	badFiles = os.listdir(bad_test_location)

	print "Processing good test files..."
	print "Storing in " + output_good_C + "/ and " + output_good_CR + "/"
	for f in goodFiles:
		if os.path.isfile(good_test_location+"/"+f):
			name,ext = f.split(".")
			os.popen("./"+compiler1 +" "+good_test_location+"/"+f+" > "+output_good_C+"/"+name+".txt 2> /dev/null")
			os.popen("./"+compiler2 +" "+good_test_location+"/"+f+" > "+output_good_CR+"/"+name+".txt 2> /dev/null")
		
	print "Processing bad test files..."
	print "Storing in " + output_bad_C + "/ and " + output_bad_CR + "/"
	for f in badFiles:
		if os.path.isfile(bad_test_location+"/"+f):
			name,ext = f.split(".")
			os.popen("./"+compiler1 +" "+bad_test_location+"/"+f+" > "+output_bad_C+"/"+name+".txt 2> /dev/null")
			os.popen("./"+compiler2 +" "+bad_test_location+"/"+f+" > "+output_bad_CR+"/"+name+".txt 2> /dev/null")

	print "Files generated successfully.\n"
	return True
	
def generateDiff():
	"""docstring for generateDiff"""

	print "Generating diff from good test files, stored in " + goodDiff
	os.popen("diff " + output_good_C + "/ " + output_good_CR + "/ >> " + goodDiff)

	print "Generating diff from bad test files, stored in " + badDiff
	os.popen("diff " + output_bad_C + "/ " + output_bad_CR + "/ >> " + badDiff)

	print "Diffs Generated successfully\n"
	return True
	
def verifyDiffFiles():
	"""docstring for verifyDiffFiles"""
	
	print "Verifying the Diff files..."
	ghandle = open(goodDiff, "r")
	
	s = ""
	for line in ghandle:
		s = s+line
	ghandle.close()
	
	if s in string.whitespace:
		print "Good Tests PASSED"
	else:
		print "Good Tests FAILED"
			
	os.popen('grep -e "^[0-9]*c[0-9]*" '+badDiff+' > '+tmpCmp)
	tmpDiff = open(tmpCmp, "r")
	tmpFlag = True
	for line in tmpDiff:
			one, two = line.split('c')
			one = one.strip()
			two = two.strip()
			if one != two:
				tmpFlag = False
				break
	tmpDiff.close()

	if tmpFlag == True:
		print "Bad Tests PASSED"
	elif tmpFlag == False:
		print "Bad Tests FAILED"
	
	## Uncomment if you want to enable the deletion of tmpCmp	
	#os.remove(tmpCmp)
	return True
	
	
if __name__ == '__main__':

	## Executables
	compiler1          = "espressoc"
	compiler2          = "espressocr"

	## Directories
	## DO NOT CHANGE THESE DECLARATIONS
	output_good_C      = "output_good_C"
	output_good_CR     = "output_good_CR"
	output_bad_C       = "output_bad_C"
	output_bad_CR      = "output_bad_CR"

	## Test file directories
	## These need to be updated based on whatever Phase you are testing

	good_test_location = "/Users/laxmikadariya/Documents/Education/first_sem/compiler/project/Espresso/Tests/Phase3/Espresso/GoodTests"
	bad_test_location  = "/Users/laxmikadariya/Documents/Education/first_sem/compiler/project/Espresso/Tests/Phase3/Espresso/BadTests"

	## Files to hold the diff output
	goodDiff           = "gDiff.txt"
	badDiff            = "bDiff.txt"
	tmpCmp             = "tmpCmp.txt"


	if len(sys.argv) != 2:
		sys.exit("Incorrect Usage: Supply a command to run, options are: \n   -clean\n   -gen\n   -diff\n   -verify\n   -runall\n")

	if (sys.argv[1] == "-runall"):
		clean()
		verifyFileSystemStructure()
		generate()
		generateDiff()
		verifyDiffFiles()
	else: 	

		if sys.argv[1] == "-clean":
			clean()
			sys.exit()

		verifyFileSystemStructure()
		
		if(sys.argv[1] == "-gen"):
			generate()
			
		if (sys.argv[1] == "-diff"):
			generateDiff()

		if (sys.argv[1] == "-verify"):
			verifyDiffFiles()
	
	