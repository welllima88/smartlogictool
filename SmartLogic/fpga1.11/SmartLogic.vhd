library ieee;
--library UNISIM;
use IEEE.std_logic_1164.all;
use IEEE.numeric_std.all;
--use UNISIM.vcomponents.all;

entity SmartLogic is
   port(
      pc			: in unsigned(7 downto 0);
      pb			: out unsigned(7 downto 0);
      CLK		: in std_logic;
		sc_clk	: in std_logic;
		sc_rst	: in std_logic;
		sc_io		: inout std_logic;
		--sc_mirror: out std_logic;
		usb_rst	: out std_logic;
		usb_clk	: in std_logic; -- cdatau
		usb_snd 	: in std_logic; -- sdatau
		set_atr	: in std_logic;
		get_baud	: in std_logic;
      fpga_clk	: out std_logic;-- cdataf
		fpga_snd	: out std_logic -- sdataf
   );
--	attribute period: string;
--	attribute period of CLK : signal is "20 ns";
end SmartLogic;

architecture RTL of SmartLogic is
	type comm_status is (idle, sending, receiving);
	signal state : comm_status;
	signal sc_state : comm_status;
	
	-- smartcard to memory
	signal ipos : std_logic;
	signal spos : std_logic;
	signal irec : std_logic;
	
	signal obit : std_logic;
	
	-- memory to smartcard
	signal ibuffer : unsigned(0 to 63);
	signal obuffer : unsigned(0 to 63) := X"FFFFFFFFFFFFFFFF";
	signal atr_buffer : unsigned(0 to 255);
	signal atr_max		: unsigned(0 to 7);

	-- baudrate detection process
	signal bdctrs : unsigned(0 to 63);
		
	-- DCM signals
	--signal CLK_div		: std_logic;
	
	--WHEN DCM IS USED:
	--signal logic_0		: std_logic;
	--signal CLK_int, CLK_dcm : std_logic;
	--signal CLKFB_in, CLK0_buf : std_logic;
	
begin

	sc_io <= '0' when obit='0' else 'Z';
	usb_rst <= sc_rst;


-- Clock runs on 48MHz... if slower speed is needed, uncomment this DCM.
-- Currently configured to divide by 1.5!
--U3 : BUFG port map (I => CLK, O => CLK_div);

--   -- DCM: Digital Clock Manager Circuit
--   -- Spartan-3
--   -- Xilinx HDL Language Template, version 11.4
--	logic_0 <= '0';
--	U1 : IBUFG port map ( I => CLK, O => CLK_int); 
--	U3 : BUFG port map (I => CLK_dcm, O => CLK_div);
--	U4 : BUFG port map ( I => CLK0_buf, O => CLKFB_in);
--   DCM_inst : DCM
--   generic map (
--      CLKDV_DIVIDE => 1.5, --  Divide by: 1.5,2.0,2.5,3.0,3.5,4.0,4.5,5.0,5.5,6.0,6.5
--                           --     7.0,7.5,8.0,9.0,10.0,11.0,12.0,13.0,14.0,15.0 or 16.0
--      CLKFX_DIVIDE => 1,   --  Can be any integer from 1 to 32
--      CLKFX_MULTIPLY => 4, --  Can be any integer from 2 to 32
--      CLKIN_DIVIDE_BY_2 => FALSE, --  TRUE/FALSE to enable CLKIN divide by two feature
--      CLKIN_PERIOD => 20.833,          --  Specify period of input clock
--      CLKOUT_PHASE_SHIFT => "NONE", --  Specify phase shift of NONE, FIXED or VARIABLE
--      CLK_FEEDBACK => "1X",         --  Specify clock feedback of NONE, 1X or 2X
--      DESKEW_ADJUST => "SYSTEM_SYNCHRONOUS", --  SOURCE_SYNCHRONOUS, SYSTEM_SYNCHRONOUS or
--                                             --     an integer from 0 to 15
--      DFS_FREQUENCY_MODE => "LOW",     --  HIGH or LOW frequency mode for frequency synthesis
--      DLL_FREQUENCY_MODE => "LOW",     --  HIGH or LOW frequency mode for DLL
--      DUTY_CYCLE_CORRECTION => TRUE, --  Duty cycle correction, TRUE or FALSE
--      FACTORY_JF => X"8080",          --  FACTORY JF Values
--      PHASE_SHIFT => 0,        --  Amount of fixed phase shift from -255 to 255
--      SIM_MODE => "SAFE", -- Simulation: "SAFE" vs "FAST", see "Synthesis and Simulation
--                          -- Design Guide" for details
--      STARTUP_WAIT => FALSE) --  Delay configuration DONE until DCM LOCK, TRUE/FALSE
--   port map (
--      CLK0 => CLK0_buf,     -- 0 degree DCM CLK ouptput
--      CLK180 => open, -- 180 degree DCM CLK output
--      CLK270 => open, -- 270 degree DCM CLK output
--      CLK2X => open,   -- 2X DCM CLK output
--      CLK2X180 => open, -- 2X, 180 degree DCM CLK out
--      CLK90 => open,   -- 90 degree DCM CLK output
--      CLKDV => CLK_dcm,   -- Divided DCM CLK out (CLKDV_DIVIDE)
--      CLKFX => open,   -- DCM CLK synthesis out (M/D)
--      CLKFX180 => open, -- 180 degree CLK synthesis out
--      LOCKED => open, -- DCM LOCK status output
--      PSDONE => open, -- Dynamic phase adjust done output
--      STATUS => open, -- 8-bit DCM status bits output
--      CLKFB => CLKFB_in,   -- DCM clock feedback
--      CLKIN => CLK_int,   -- Clock input (from IBUFG, BUFG or DCM)
--      PSCLK => logic_0,   -- Dynamic phase adjust clock input
--      PSEN => logic_0,     -- Dynamic phase adjust enable input
--      PSINCDEC => logic_0, -- Dynamic phase adjust increment/decrement
--      RST => logic_0        -- DCM asynchronous reset input
--   );

   -- End of DCM_inst instantiation


	baudrate: process(CLK,sc_clk,sc_rst,usb_clk)
		variable bdctr		: unsigned(0 to 63) := "0000000000000000000000000000000000000000000000000000000000000000";
		variable CLK_ctr	: unsigned(0 to 19) := "00000000000000000000";
		variable rst_his  : std_logic := '0';
		variable baudset	: boolean := false;

		begin
			bdctrs <= bdctr;
		
			if rising_edge(sc_clk) then
				bdctr(0 to 31) := bdctr(0 to 31) + 1;
			end if;
			
			if rising_edge(CLK) then
				if sc_rst = '1' and rst_his = '0' then
					CLK_ctr := "00000000000000000000";
					bdctr(32 to 63) := "00000000000000000000000000000000";
					baudset := false;
				elsif CLK_ctr < 1000000 then
					CLK_ctr := CLK_ctr + 1;
				elsif not(baudset) then
					baudset := true;
					bdctr(32 to 63) := bdctr(0 to 31);
				end if;
				rst_his := sc_rst;
			end if;
		
		end process baudrate;

	memory: process(CLK,irec,ipos)
		variable atr : unsigned(0 to 255);
		variable atrmax : unsigned(0 to 7) := "00000000";
		variable atrreal : unsigned(0 to 4) := "00000";		
		variable atrset : boolean := false;

		variable fpga_send	: std_logic := '0'; -- FLAG FOR SENDING FPGA => USB
		variable fpga_clock	: std_logic := '0'; -- CLOCK FOR FPGA-USB COMMUNICATION
		
		variable iptrc		: unsigned(0 to 2) := "000"; -- POINTER IN RECEIVE BUFFER
		variable optr		: unsigned(0 to 2) := "000"; -- POINTER IN SEND BUFFER
		
		variable ihis		: std_logic := '0'; -- LAST ACTIVE BUFFER
		variable ichg		: std_logic := '0'; -- BUFFER SWITCH
		variable cchg		: std_logic := '0'; -- CLOCK CHANGE (FPGA-USB-CLOCK)
		
		variable irdy		: boolean := false; -- RECEIVE BUFFER READY
		variable ordy		: boolean := false; -- SEND BUFFER READY
		variable istart	: boolean := false; -- START FLUSHING TO USB
		variable ostart	: boolean := false; -- RECEIVING FROM USB HAS STARTED
		variable icheck   : boolean := true;  -- CHECK LAST BYTE OF BUFFER WAS FLUSHED
		variable has_been_low : boolean := false; -- IO HAS BEEN LOW ==> SENDING HAS STARTED

		variable bptr		: unsigned(0 to 2) := "000"; -- BAUDRATE POINTER
		
		begin

			fpga_snd <= fpga_send;
			fpga_clk <= fpga_clock;
			atr_buffer <= atr;
			atr_max <= atrmax;
			
			if rising_edge(CLK) then

				if ichg = '1' then
					if ihis = '1' then
						iptrc := "100";
						optr := "100";
						obuffer(32 to 63) <= X"FFFFFFFF";
					else
						iptrc := "000";
						optr := "000";
						obuffer(0 to 31) <= X"FFFFFFFF";
					end if;
					irdy := true;
					istart := true;
					icheck := true; -- HAS CHECK BEEN DONE AFTER 4TH BYTE?
					ihis := not(ihis);
					ichg := '0';
					ordy := true;
					ostart := false;
				elsif ipos = not(ihis) then
					ichg := '1';
				elsif set_atr = '1' and usb_snd = '1' then
					if not(atrset) then
						atrset := true;
						atrmax := "00000000";
						atrreal := "00000";
					end if;
					if fpga_clock = not(usb_clk) then
						if atrmax = "00000000" then
							atrmax := pc;
						else
							atr((to_integer(atrreal) * 8) to (to_integer(atrreal) * 8) + 7) := pc;
							atrreal := atrreal + 1;
						end if;
						fpga_clock := usb_clk;
					end if;
				elsif get_baud = '1' and usb_snd = '0' then
					if bdctrs(32 to 63) = "00000000000000000000000000000000" then
						fpga_clock := '0';
					else
						fpga_clock := '1';
					end if;
				elsif get_baud = '1' and usb_snd = '1' then
					if fpga_clock = usb_clk then
						pb <= bdctrs(to_integer(bptr) * 8 to (to_integer(bptr) * 8) + 7);
						bptr := bptr + 1;
						fpga_clock := not(fpga_clock);
					end if;
				elsif usb_snd = '1' then
					state <= sending;
					has_been_low := false;
					if cchg = '1' then
							fpga_clock := usb_clk;
							cchg := '0';
					elsif fpga_clock = not(usb_clk) and ordy and (spos = '0' or ostart) then
						obuffer(to_integer(optr) * 8 to (to_integer(optr) * 8) + 7) <= pc;
						--obuffer(to_integer(optr) * 8 to (to_integer(optr) * 8) + 7) <= "10101010";
						ostart := true;
						if optr(1 to 2) = "11" then
							ordy := false;
							ostart := false;
						else
							optr := optr + 1;
						end if;
						cchg := '1';
					end if;
				elsif fpga_send = '1' or irec = '1' then --fpga_send = '1' or
					obuffer(0 to 63) <= X"FFFFFFFFFFFFFFFF";
					state <= receiving;
					fpga_send := '1';
					if cchg = '1' then
							fpga_clock := not(fpga_clock);
							cchg := '0';
					elsif fpga_clock = usb_clk and icheck = false then -- irec='0'
						if irec = '0' then
							fpga_send := '0';
							state <= idle;
						end if;
						icheck := true;
					elsif (fpga_clock = usb_clk or istart) and irdy then
						istart := false;
						pb <= ibuffer(to_integer(iptrc) * 8 to (to_integer(iptrc) * 8) + 7);
						if iptrc(1 to 2) = "11" then
							irdy := false;
							icheck := false;
						else
							iptrc := iptrc + 1;
						end if;
						cchg := '1';
					end if;
				else -- state = idle/sending
					atrset := false;
					ostart := false;
					bptr := "000";
					cchg := '0';
					if state = sending then
						if sc_state = sending then
							has_been_low := true;
						elsif sc_state = idle and has_been_low then
							state <= idle;
							obuffer(0 to 63) <= X"FFFFFFFFFFFFFFFF";
						end if;
					end if;
				end if;

			end if;
		end process memory;

	smartcard: process(sc_clk,sc_rst,sc_io,state) -- sc_rst a.o. to advice of xilinx
		variable iptr		: unsigned(0 to 5) := "000000";
		variable hptr		: std_logic := '0';
		
		variable cycles	: unsigned(0 to 8) := "000000000";
		variable sync		: unsigned(0 to 8) := "000000000";
		variable etu		: unsigned(0 to 1) := "00";
		
		variable outbuffer : unsigned(0 to 11) := "111111111111";
		
		variable mbit		: std_logic_vector(0 to 1) := "00";
		
		variable send_atr	: boolean := false;
		variable atr_ptr	: unsigned(0 to 7);
		variable atr_wait	: boolean := false;
		
		variable rst_his	: std_logic := '1';		
		variable high_his	: unsigned(0 to 6) := "0000000";

		begin
			ipos <= iptr(0);
			spos <= iptr(1);
			
			-- sc_mirror <= outbuffer(1);
			
			if rising_edge(sc_clk) then

				-- CLOCK
				if cycles > 370 then
					cycles := "000000000";
				else
					if cycles(3 to 8) = "00000" then
						-- TAKE SAMPLES OF IO LINE
						-- EVERY 32 CYCLES
						mbit := mbit(1) & sc_io;
					end if;
					cycles := cycles + 1;
				end if;

				if sc_rst = '1' and rst_his = '0' and not(send_atr) then -- and not(send_atr)
					-- RESET ATR PARAMETERS
					send_atr := true;
					atr_wait := true;
					atr_ptr := "00000000";
					etu := "00";
					-- CLEAR RECEIVE FLAG
					irec <= '0';
					-- CLEAR RECEIVING BUFFER
					ibuffer <= X"FFFFFFFFFFFFFFFF";
				elsif cycles = sync then
					-- ETU COUNTER
					if etu > 2 then
						atr_wait := false;
						etu := "00";
					else
						etu := etu + 1;
					end if;

					-- DATA POINTER
					if iptr = 63 then
						iptr := "000000";
					else
						iptr := iptr + 1;
					end if;

					-- DATA OUT ON IO
					outbuffer := outbuffer(1 to 11) & obuffer(to_integer(iptr));
					if(send_atr) then
						-- SEND ATR FROM ATR BUFFER
						if atr_wait then
							obit <= '1';
						else
							if atr_ptr >= atr_max then
								send_atr := false;
								obit <= '1';
							else
								obit <= atr_buffer(to_integer(atr_ptr));
								atr_ptr := atr_ptr + 1;
							end if;
						end if;
					else
						-- SEND REGULAR BUFFER
						obit <= outbuffer(1);
					end if;
					
					-- DATA IN ON IO
					if (state = receiving and not(send_atr)) then
						ibuffer(to_integer(iptr)) <= sc_io;
					else
						ibuffer(to_integer(iptr)) <= '1';
					end if;
					
					-- SIGNAL INDICATOR FOR USB PROCESS: OUTGOING
					if outbuffer = "111111111111" then
						sc_state <= idle;
					else
						sc_state <= sending;
					end if;
					
					-- SIGNAL INDICATOR FOR USB PROCESS: INCOMING
					if sc_io = '0' then
						high_his := "0000000";
					elsif high_his < 17 then
						high_his := high_his + 1;
						hptr := iptr(0);
					elsif hptr = not(iptr(0)) then
						-- BUFFER SWITCH AFTER RECEIVING MANY ONES
						-- CLEAR RECEIVE FLAG
						irec <= '0';
						--ibuffer <= X"FFFFFFFFFFFFFFFF";
					end if;

				elsif not(send_atr) and state /= sending then
					-- SYNCHRONIZE DURING IDLE TIME OR WHEN RECEIVING
					-- SIGNAL DROP DETECTION IN mbit WHICH IS NOT CAUSED BY outbuffer
					if mbit = "10" and outbuffer(0 to 1) = "11" then
						-- SYNCHRONIZE AT HALF A BIT PERIOD
						sync := cycles;
						if (sync + 170) > 370 then
							sync := sync - 201;
						end if;
						-- SET RECEIVE FLAG
						irec <= '1';
					end if;
				end if;

				rst_his := sc_rst;
				
			end if;

		end process smartcard;
	 
end RTL;
