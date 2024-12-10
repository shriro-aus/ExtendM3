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
 Extension Name: EXT100MI/UpdApprovalLine
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date:
 Description:
 * This script updates line details in the EXTOLN tables
 * within the M3 system. It validates input data, updates the order lines, and manages
   audit trails.
 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created
******************************************************************************************/

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

public class UpdApprovalLine extends ExtendM3Transaction {

    private final MIAPI mi
    private final LoggerAPI logger
    private final DatabaseAPI database
    private final MICallerAPI miCaller

    // Variables for input fields
    private String inCONO, inORNO, inPONR, inPOSX, inWSTA, inSTOP, inTSID, inRGDT, inLMDT, inCHID, inRGTM, inCHNO
    private boolean isValidInput = true

    // Get the current date and time
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern('yyyyMMdd')
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern('HHmmss')
    int currentDate = dateFormatter.format(LocalDate.now()).toInteger()
    int currentTime = timeFormatter.format(LocalTime.now()).toInteger()

    /**
     * Constructor to initialize necessary APIs.
     *
     * @param mi       The MIAPI interface for input and output operations.
     * @param logger   The LoggerAPI interface for logging messages.
     * @param program  The ProgramAPI interface for program-related operations.
     * @param database The DatabaseAPI interface for database operations.
     * @param utility  The UtilityAPI interface for utility functions.
     * @param miCaller The MICallerAPI interface for MI calling operations.
     */
    public UpdApprovalLine(MIAPI mi, LoggerAPI logger, DatabaseAPI database, MICallerAPI miCaller) {
        this.mi = mi
        this.logger = logger
        this.database = database
        this.miCaller = miCaller
    }

    public void main() {
        getApiInput()
        validateInput()
        if (validateInput()) {
            updateOrderLine()
        }
    }

    /**
     * Method to fetch input data from the API and initialize member variables.
     */
    private void getApiInput() {
        // Read and trim the input fields
        inCONO = mi.inData.get('CONO') // (3) Company
        inORNO = mi.inData.get('ORNO').trim() // (10) Order number
        inPONR = mi.inData.get('PONR') // (5) PO line number
        inWSTA = mi.inData.get('WSTA').trim() // Workflow Status

        // Handle null or empty POSX value
        inPOSX = mi.inData.get('POSX') == null || mi.inData.get('POSX').trim().isEmpty() || mi.inData.get('POSX').trim().equals('?') ? '0' : mi.inData.get('POSX').trim()

        // Handle null or empty STOP value
        inSTOP = mi.inData.get('STOP') == null || mi.inData.get('STOP').trim().isEmpty() || mi.inData.get('STOP').trim().equals('?') ? '1' : mi.inData.get('STOP').trim()
        inCHID = mi.inData.get('CHID').trim() // Changed by
        inTSID = mi.inData.get('TSID').trim() // TSID
    }

    /**
     * Method to validate the input fields for correctness.
     */
    private boolean validateInput() {
        isValidInput = checkValidCompany()

        if (isValidInput == false) {
            return false
        }
        isValidInput = checkValidOrderNumber()

        if (isValidInput == false) {
            return false
        }

        isValidInput = checkValidLineNumber()

        if (isValidInput == false) {
            return false
        }

        // Validate workflow status - should be either 10, 20, 30 ,40 ,50
        if (!inWSTA.equals('')) {
            if (!(inWSTA.equals('10') || inWSTA.equals('20') || inWSTA.equals('30') || inWSTA.equals('40') || inWSTA.equals('50'))) {
                mi.error('Invalid Workflow Status: Should be 10 (InProcess), 20 (Approved), 20 (Rejected), 40 (Copied), 50 (cancelled).')
                return false
            }
        }
        return true
    }

    public void updateOrderLine() {
        // EXTOLN table update
        DBAction action = database.table('EXTOLN')
            .index('00')
            .build()
        DBContainer EXTOLN_container = action.getContainer()
        EXTOLN_container.set('EXCONO', inCONO.toInteger())
        EXTOLN_container.set('EXORNO', inORNO)
        EXTOLN_container.setInt('EXPONR', inPONR.toInteger())
        EXTOLN_container.set('EXPOSX', Integer.parseInt(inPOSX))

        if (action.read(EXTOLN_container)) {
            action.readLock(EXTOLN_container, updateCallBack) // Lock and update other fields
        } else {
            mi.error('Update failed: Record not found')
            return
        }
    }

    /**
     * Callback to update fields in the EXTOLN table.
     * This method updates the workflow status, STOP, TSID, and audit trail fields.
     */
    Closure<?> updateCallBack = { LockedResult lockedResult ->
        // Update Workflow Status, STOP, and TSID
        if (!inWSTA.equals('')) {
            lockedResult.set('EXWSTA', inWSTA)
        }
        if (!inSTOP.equals('')) {
            lockedResult.set('EXSTOP', inSTOP)
        }
        if (!inTSID.equals('') && inWSTA.equals('20')) {
            lockedResult.set('EXTSID', inTSID.toInteger())
        }

        // Set audit trail fields
        lockedResult.set('EXCHNO', lockedResult.getInt('EXCHNO') + 1) // Change Number
        lockedResult.set('EXCHID', inCHID) // Changed by
        lockedResult.set('EXLMDT', currentDate) // Last Modified Date

        lockedResult.update() // Update the result
    }

    /**
     * Validate the existence of the company code in the system.
     * It checks if the company code is numeric and exists in the CMNCMP table.
     */
    public boolean checkValidCompany() {
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Invalid Company Code: '${inCONO}'. Company code should only contain numeric values.")
            return false
        }

        DBAction query = database.table('CMNCMP')
            .index('00')
            .build()
        DBContainer container = query.getContainer()
        container.set('JICONO', inCONO.toInteger())

        if (!query.read(container)) {
            mi.error("Company Code '${inCONO}' not found in the system. Please verify and try again.")
            return false
        }
        return true
    }

    /**
     * Validate the order number by checking its length and existence in the OOHEAD table.
     */
    public boolean checkValidOrderNumber() {
        if (inORNO.toString().length() < 10) {
            mi.error("Invalid Order Number: '${inORNO}'. Order number must be at least 10 characters long.")
            return false
        }

        DBAction query = database.table('OOHEAD')
            .index('00')
            .build()
        DBContainer container = query.getContainer()
        container.set('OACONO', inCONO.toInteger())
        container.set('OAORNO', inORNO)

        if (!query.read(container)) {
            mi.error("Order Number '${inORNO}' not found for Company Code '${inCONO}'. Please verify the order number.")
            return false
        }
        return true
    }

    /**
     * Validate the line number by ensuring it contains only numeric values
     * and that it exists in the EXTOLN table.
     */
    public boolean  checkValidLineNumber() {
        // Ensure line number contains only numeric values
        if (!inPONR =~ /^[0-9]+$/) {
            mi.error("Invalid Line Number: The line number ${inPONR} must only contain numeric values.")
            return false
        }

        DBAction query = database.table('EXTOLN').index('00').build()
        DBContainer container = query.getContainer()
        container.set('EXCONO', inCONO.toInteger())
        container.set('EXORNO', inORNO)
        container.set('EXPONR', inPONR.toInteger())

        if (!inPOSX.equals('0')) {
            container.set('EXPOSX', inPOSX.toInteger())
        }

        // Check if line number exists
        if (!query.read(container)) {
            mi.error('Line Number Not Found In EXTOLN table')
            return false
        }
        return true
    }

}
