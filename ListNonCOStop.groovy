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
 Extension Name: EXT100MI/ListNonCOStop
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date:
 Description:
 * This script lists orderNumber where line stop 1 in databse records from the EXTOLN table based on the company
   number provided. It validates the input, retrieves records, and outputs relevant
   details based on specified criteria.

 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created
******************************************************************************************/
public class ListNonCOStop extends ExtendM3Transaction {

    private final MIAPI mi
    private final LoggerAPI logger
    private final DatabaseAPI database
    private final MICallerAPI miCaller

    private String inCONO
    private final Set<String> processedOrderNumbers = new HashSet<>()

    /*
       * Transaction EXT100MI/ListNonCOStop Interface
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param program - Infor Program Interface
     * @param database - Infor Database Interface
     * @param utility - Infor Utility Interface
     * @param miCaller - Infor MI Caller Interface
     */
    public ListNonCOStop(MIAPI mi, LoggerAPI logger, DatabaseAPI database, MICallerAPI miCaller) {
        this.mi = mi
        this.logger = logger
        this.database = database
        this.miCaller = miCaller
    }

    public void main() {
        getApiInput()
        validateInput()
        getOrderNumbersOfStopLines()
    }

    /*
     * Retrieves input parameters from the API.
     */
    private void getApiInput() {
        inCONO = mi.inData.get('CONO')
    }

    /*
     * Validates the company number input.
     */
    private void validateInput() {
        checkValidCompany()
    }

    /*
     * Validates the company number format and existence in the CMNCMP table.
     */
    public void checkValidCompany() {
        // Check if the company number is numeric
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error('Company number must only contain numeric values.')
            return
        }

        // Query the CMNCMP table to validate the company
        DBAction query = database.table('CMNCMP').index('00').build()
        DBContainer container = query.getContainer()
        container.set('JICONO', inCONO.toInteger())

        // If the company is not found, return an error
        if (!query.read(container)) {
            mi.error("Company with number ${inCONO} not found.")
            return
        }
    }

    private void getOrderNumbersOfStopLines() {
        // Build a query for the EXTOLN table
        DBAction action = database.table('EXTOLN').index('00').selection('EXCONO', 'EXORNO', 'EXSTOP', 'EXHSTP').build()
        DBContainer container = action.createContainer()
        container.set('EXCONO', inCONO.toInteger())

        Closure<?> listRecords = { DBContainer data ->
            String orderNumber = data.get('EXORNO').toString().trim()

            // Process only if the order is not already processed, and meets the criteria
            if (!processedOrderNumbers.contains(orderNumber) && inCONO.equals(data.get('EXCONO').toString().trim()) && data.get('EXSTOP').toString().trim().equals('1')) {
                // Add the order number to the set of processed orders
                processedOrderNumbers.add(orderNumber)

                String currentHStp = data.get('EXHSTP').toString().trim()
                // Only output if the EXHSTP field is not equal to 8
                if (!currentHStp.equals('8')) {
                    mi.outData.put('ORNO', orderNumber)
                    mi.outData.put('CONO', data.get('EXCONO').toString().trim())
                    mi.write()  // Output the result
                }
            }
        };

        int nrOfRecords = 10000  // Set a maximum of 10,000 records
        action.readAll(container, 1, nrOfRecords, listRecords)
    }

}
