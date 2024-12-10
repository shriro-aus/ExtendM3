/*
 ***************************************************************
 *                                                             *
 *                           NOTICE                            *
 *                                                             *
 *   THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS             *
 *   CONFIDENTIAL INFORMATION OF INFOR AND/OR ITS AFFILIATES   *
 *   OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED WITHOUT PRIOR  *
 *   WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND       *
 *   ADAPT THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH  *
 *   THE TERMS OF THEIR SOFTWARE LICENSE AGREEMENT.            *
 *   ALL OTHER RIGHTS RESERVED.                                *
 *                                                             *
 *   (c) COPYRIGHT 2020 INFOR.  ALL RIGHTS RESERVED.           *
 *   THE WORD AND DESIGN MARKS SET FORTH HEREIN ARE            *
 *   TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR          *
 *   AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS        *
 *   RESERVED.  ALL OTHER TRADEMARKS LISTED HEREIN ARE         *
 *   THE PROPERTY OF THEIR RESPECTIVE OWNERS.                  *
 *                                                             *
 ***************************************************************
 */

/****************************************************************************************
 Extension Name: EXT100MI/AddApprovalLine
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date:
 Description:
 * This script processes approval lines in M3.
 * validates input values, and inserts records in the EXTOLN tables.
 * The workflow status is checked, and CoStop information is handled appropriately.

 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01          1.0              Initial version created

******************************************************************************************/
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

public class AddApprovalLine extends ExtendM3Transaction {

    private final MIAPI mi
    private final LoggerAPI logger
    private final ProgramAPI program
    private final DatabaseAPI database
    private final MICallerAPI miCaller

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern('yyyyMMdd')
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern('HHmmss')

    int currentDate = dateFormatter.format(LocalDate.now()).toInteger()
    int currentTime = timeFormatter.format(LocalTime.now()).toInteger()

    /*
     * Transaction EXT100MI/AddApprovalLine Interface
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param program - Infor Program Interface
     * @param database - Infor Database Interface
     * @param miCaller - Infor MI Caller Interface
     */
    public AddApprovalLine(MIAPI mi, LoggerAPI logger, ProgramAPI program,
                           DatabaseAPI database, MICallerAPI miCaller) {
        this.mi = mi
        this.logger = logger
        this.program = program
        this.database = database
        this.miCaller = miCaller
                           }

    // API Input Variables
    private String inCONO
    private String inPONR
    private String inORNO
    private String inPOSX
    private String inWSTA
    private int inCHNO
    private String inTSID
    private String inHSTP
    private String inRGDT
    private String inLMDT
    private String inCHID
    private String inRGTM
    private boolean isValidInput = true
    private int oolineChangeNo
    public void main() {
        getApiInput()
        updateHeaderCoStopInExtoln()
        if (validateApiInput()) {
            addInExtoln()
        }
    }

    /*
     * Retrieves input parameters from the API
     */
    private void getApiInput() {
        inCONO = mi.inData.get('CONO') // Company
        inORNO = mi.inData.get('ORNO').trim() // Order number
        inPONR = mi.inData.get('PONR') // PO line number
        inPOSX = (mi.inData.get('POSX') == null || mi.inData.get('POSX').trim().isEmpty()) ? '0' : mi.inData.get('POSX').trim() // Line suffix
        inWSTA = mi.inData.get('WSTA').trim() // Workflow status
        inTSID = mi.inData.get('TSID').trim().isEmpty() ? '0' : mi.inData.get('TSID').trim() // Task ID, set to "0" if empty
        inCHID = mi.inData.get('CHID').trim() // Changed by user ID
    }

    /*
     * Retrieves CoStop info Of order from OIS120MI
     */
    private void updateHeaderCoStopInExtoln() {
        Map<String, String> parameters = ['ORNO': inORNO]
        Closure<?> handlerItm = { Map<String, String> response ->
            inHSTP = response.OBLC // Retrieve stop information
        };
        miCaller.call('OIS120MI', 'GetCOStopInfo', parameters, handlerItm)
    }

    /*
     * Validates the input received from API
     */
    private boolean validateApiInput() {
        isValidInput = checkValidCompany()

        if (isValidInput == false) {
            return false
        }
        isValidInput = checkValidOrderNumber()

        if (isValidInput == false) {
            return false
        }

        // isValidInput = checkValidLineNumber()

        if (isValidInput == false) {
            return false
        }

        // Validate workflow status - should be either 10, 20, 30 ,40 ,50
        if (!inWSTA.equals('')) {
            if (!(inWSTA.equals('10') || inWSTA.equals('20') || inWSTA.equals('30') || inWSTA.equals('40') || inWSTA.equals('50'))) {
                mi.error('Invalid Workflow Status: Should be 10 (InProcess), 20 (Approved), 30 (Rejected), 40 (Copied), 50 (cancelled).')
                return false
            }
        }

        return true
    }

    /*
     * Inserts a new record in the EXTOLN table
     */
    private void addInExtoln() {
        DBAction action = database.table('EXTOLN').index('00').build()
        DBContainer container = action.createContainer()

        container.set('EXCONO', Integer.parseInt(inCONO)) // Company
        container.set('EXORNO', inORNO)                   // Order number
        container.set('EXPONR', inPONR.toInteger())        // PO line number
        container.set('EXPOSX', Integer.parseInt(inPOSX))  // Line suffix

        if (action.read(container)) {
            mi.error('Duplicate Record: Line must be unique.') // Error if record exists
            return
        }

       /*
        *  If order status 40 then do not set TSID for copied Credit Type order when adding.
        */

        if (!inWSTA.equals('40')) {
            container.set('EXTSID', inTSID.toInteger())      // Task ID
        } else {
            container.set('EXTSID', 0)
        }

        container.set('EXRGDT', currentDate)             // Registration date
        container.set('EXRGTM', currentTime)             // Registration time
        container.set('EXCHNO', 0)                       // Change number
        container.set('EXCHID', inCHID)                  // Changed by user
        container.set('EXLMDT', currentDate)             // Last modified date
        container.set('EXWSTA', inWSTA)                  // Workflow status
        container.set('EXHSTP', inHSTP.toInteger())      // Header CoStop

      /*
       * If order type is Credit and it is copied then set Exstop to 0.
       */
        if (!inWSTA.equals('40')) {
            container.set('EXSTOP', '1')
        } else {
            container.set('EXSTOP', '0')
        }

        action.insert(container)
    }

    /*
     * Validates company input
     */
    public boolean checkValidCompany() {
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error('Invalid Company Code: Company should contain only numeric values.')

            return false
        }

        DBAction query = database.table('CMNCMP').index('00').build()
        DBContainer container = query.getContainer()
        container.set('JICONO', inCONO.toInteger())

        if (!query.read(container)) {
            mi.error("Company Not Found: The company code ${inCONO} does not exist.")
            return false
        }

        return true
    }

    /*
     * Validates order number input
     */
    public boolean checkValidOrderNumber() {
        if (inORNO.toString().length() < 10) {
            mi.error('Invalid Order Number: Order number must be at least 10 characters.')
            return false
        }

        DBAction query = database.table('OOHEAD').index('00').build()
        DBContainer container = query.getContainer()
        container.set('OACONO', inCONO.toInteger())
        container.set('OAORNO', inORNO)

        if (!query.read(container)) {
            mi.error("Order Not Found: The order number ${inORNO} does not exist in company ${inCONO}.")
            return false
        }
        return true
    }

    /*
     * Validates PO line number input
     */
    public boolean checkValidLineNumber() {
        if (!inPONR =~ /^[0-9]+$/) {
            isValidInput = false
            mi.error('Invalid Line Number: Line number should contain only numeric values.')
            return false
        }

        DBAction query = database.table('OOLINE').index('00').build()
        DBContainer container = query.getContainer()
        container.set('OBCONO', inCONO.toInteger())
        container.set('OBORNO', inORNO)
        container.set('OBPONR', inPONR.toInteger())

        if (!inPOSX.equals('0')) {
            container.set('OBPOSX', inPOSX.toInteger())
        }

        if (!query.read(container)) {
            mi.error("Line Not Found: The line number ${inPONR} does not exist in order ${inORNO} for company ${inCONO}.")
            return false
        }

        return true
    }

}
